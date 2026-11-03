-- Review decisions now notify the student who submitted (FR-NOT, submission feedback). V15 is applied and
-- stays as it is, so the type list is widened here.
ALTER TABLE notifications DROP CHECK ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'INVITATION_RECEIVED', 'INVITATION_ACCEPTED', 'INVITATION_DECLINED', 'MEMBER_REMOVED',
    'SUBMISSION_ACCEPTED', 'SUBMISSION_CHANGES_REQUESTED'));
