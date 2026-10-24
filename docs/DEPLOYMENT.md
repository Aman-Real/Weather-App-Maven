# Deploying SkyCast to the cloud

These steps are a guide only: nothing in this repository deploys automatically. Every option starts
from the same container images:

```bash
for m in discovery-server auth-service weather-service alert-service api-gateway; do
  docker build --build-arg MODULE=$m -t <registry>/skycast/$m:1.0.0 .
  docker push <registry>/skycast/$m:1.0.0
done
```

Every option also needs the same secrets: `JWT_SECRET` (32+ random characters), `DB_PASSWORD` and
`ADMIN_PASSWORD`. Use the platform's secret store; never put secrets in YAML files or git.

## Option A — any Kubernetes cluster (Minikube, AKS, EKS, GKE)

```bash
# plain manifests
kubectl apply -f k8s/
kubectl -n skycast get pods -w
kubectl -n skycast port-forward svc/api-gateway 8080:8080

# or Helm
helm upgrade --install skycast ./helm/skycast -n skycast --create-namespace \
  --set image.registry=<registry>/skycast --set image.tag=1.0.0 \
  --set secrets.jwtSecret=$(openssl rand -hex 32) \
  --set secrets.dbPassword=<db-password> --set secrets.adminPassword=<admin-password>
```

* **Minikube:** `minikube image load skycast/<module>:1.0.0` for each image, then `minikube service -n skycast api-gateway`.
* Probes use `/actuator/health/liveness` and `/actuator/health/readiness`, so Kubernetes only sends traffic to ready pods.
* `weather-service` and `alert-service` stay at **1 replica**, because their schedulers have no distributed lock.
* In production, replace the in-cluster PostgreSQL with a managed database (next sections).

## Option B — AWS

| Piece | AWS service |
|---|---|
| Images | Amazon ECR (`aws ecr create-repository --repository-name skycast/weather-service`) |
| Containers | **EKS** (use Option A) or **ECS Fargate**: one service per module, all in one VPC |
| Database | Amazon RDS for PostgreSQL. Create `authdb`, `weatherdb` and `alertdb`, and set `DB_HOST` to the RDS endpoint |
| Secrets | AWS Secrets Manager, injected as container environment variables |
| Entry point | Application Load Balancer → `api-gateway:8080` with an ACM certificate. HTTPS is required for PWA install |
| Discovery | Keep Eureka, or on EKS use Kubernetes DNS names in place of `lb://` |

## Option C — Microsoft Azure

| Piece | Azure service |
|---|---|
| Images | Azure Container Registry (`az acr create -n skycastacr -g skycast --sku Basic`) |
| Containers | **Azure Container Apps**: one app per module in one environment. Only `api-gateway` gets external ingress. Or use **AKS** (Option A) |
| Database | Azure Database for PostgreSQL – Flexible Server |
| Secrets | Container Apps secrets or Azure Key Vault references |

Example for one service:

```bash
az containerapp create -n weather-service -g skycast --environment skycast-env \
  --image skycastacr.azurecr.io/skycast/weather-service:1.0.0 --target-port 8082 --ingress internal \
  --secrets jwt=<secret> db=<password> \
  --env-vars SPRING_PROFILES_ACTIVE=docker EUREKA_URL=http://discovery-server/eureka \
             DB_HOST=<server>.postgres.database.azure.com DB_NAME=weatherdb DB_USER=skycast \
             JWT_SECRET=secretref:jwt DB_PASSWORD=secretref:db
```

## Option D — serverless containers

Google Cloud Run and Azure Container Apps (scale-to-zero) run the same images without servers to
manage.

* **Problem:** always-on pieces do not fit scale-to-zero. The Eureka registry, the alert scheduler and the long-lived SSE connections all need a running instance.
* **Fix:** set `min-instances=1` for `discovery-server`, `alert-service` and `api-gateway`. Only `auth-service` and `weather-service` scale to zero.
* **Faster cold starts:** build native images with GraalVM (`mvnw -Pnative native:compile`, after adding the GraalVM build tools plugin), or use AWS Lambda SnapStart for Java functions.

## CI/CD hook-up

* **GitHub Actions:** add a deploy job after `docker` that logs in to the registry (`docker/login-action`), pushes the images and runs `helm upgrade --install` with the cluster credentials stored as repository secrets.
* **Jenkins:** the `Jenkinsfile` already has *Push* and *Deploy to Kubernetes* stages. Add the `dockerhub` and `skycast-jwt-secret` credentials in Jenkins.
