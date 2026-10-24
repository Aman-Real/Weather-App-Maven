{{- define "skycast.labels" -}}
app.kubernetes.io/part-of: skycast
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}
