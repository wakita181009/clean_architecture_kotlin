CREATE TYPE jira_issue_priority AS ENUM (
    'highest',
    'high',
    'medium',
    'low',
    'lowest'
);

CREATE TYPE jira_issue_type AS ENUM (
    'epic',
    'story',
    'task',
    'subtask',
    'bug'
);

CREATE TABLE jira_project
(
    id   BIGINT PRIMARY KEY,
    key  VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255)
);
CREATE INDEX idx_jira_project_key ON jira_project (key);

CREATE TABLE jira_issue
(
    id          BIGINT PRIMARY KEY,
    project_id  BIGINT              NOT NULL REFERENCES jira_project (id),
    key         VARCHAR(50)         NOT NULL UNIQUE,
    summary     VARCHAR(255)        NOT NULL,
    description JSONB,
    issue_type  jira_issue_type     NOT NULL,
    priority    jira_issue_priority NOT NULL,
    created_at  TIMESTAMPTZ         NOT NULL,
    updated_at  TIMESTAMPTZ         NOT NULL
);
CREATE INDEX idx_jira_issue_project_id ON jira_issue (project_id);
CREATE INDEX idx_jira_issue_key ON jira_issue (key);
CREATE INDEX idx_jira_issue_type ON jira_issue (issue_type);
CREATE INDEX idx_jira_issue_priority ON jira_issue (priority);
