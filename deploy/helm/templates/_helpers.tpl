{{/*
Name of the Secret holding the database / application credentials.
Set .Values.credentials.existingSecret to use a Secret you manage yourself
(it must contain DB_PASSWORD, MONGO_DB_PASSWORD, SECRET_KEY, API_GATEWAY_PASSWORD and SMTP_PASS).
*/}}
{{- define "crapi.credentialsSecretName" -}}
{{- default "crapi-credentials" .Values.credentials.existingSecret -}}
{{- end -}}
