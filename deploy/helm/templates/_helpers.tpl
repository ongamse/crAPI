{{/*
Returns the base64 value for one entry of the credentials Secret.
Precedence: explicit chart value, then the value already stored in the
cluster Secret (stable across upgrades), then a newly generated random value.
Usage: include "crapi.credential" (dict "ctx" . "key" "DB_PASSWORD" "value" .Values.x)
*/}}
{{- define "crapi.credential" -}}
{{- $existing := lookup "v1" "Secret" .ctx.Release.Namespace .ctx.Values.credentials.name -}}
{{- if .value -}}
{{- .value | toString | b64enc -}}
{{- else if and $existing $existing.data (hasKey $existing.data .key) -}}
{{- index $existing.data .key -}}
{{- else -}}
{{- randAlphaNum 24 | b64enc -}}
{{- end -}}
{{- end -}}
