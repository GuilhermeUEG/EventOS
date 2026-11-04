-- V3: política de anonimato do questionário (RN-14), atividades obrigatórias do certificado (RF-32)
-- e índices para as consultas de relatórios e frequência (RNF-08).

ALTER TABLE feedback_surveys ADD COLUMN IF NOT EXISTS anonymous_responses BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE events ADD COLUMN IF NOT EXISTS cert_policy_activities VARCHAR(2000);

CREATE INDEX IF NOT EXISTS idx_activities_event ON activities(event_id);
CREATE INDEX IF NOT EXISTS idx_speakers_activity ON speakers(activity_id);
CREATE INDEX IF NOT EXISTS idx_registrations_event_status ON registrations(event_id, status);
CREATE INDEX IF NOT EXISTS idx_registrations_user ON registrations(user_id);
CREATE INDEX IF NOT EXISTS idx_attendance_activity_user ON attendance_records(activity_id, user_id);
CREATE INDEX IF NOT EXISTS idx_attendance_user ON attendance_records(user_id);
CREATE INDEX IF NOT EXISTS idx_survey_responses_activity ON survey_responses(activity_id);
CREATE INDEX IF NOT EXISTS idx_certificates_user ON certificates(user_id);
