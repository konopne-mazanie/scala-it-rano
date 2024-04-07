SET search_path TO "public";

CREATE TABLE task(
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    is_done BOOLEAN NOT NULL,
    deadline TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    priority INTEGER NOT NULL,
    UNIQUE (name)
);

CREATE INDEX i_is_done ON task(is_done);
CREATE INDEX i_priority ON task(priority);

CREATE TABLE tag(
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    name VARCHAR(10) NOT NULL,
    UNIQUE (name)
);

CREATE TABLE task_tag(
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    task_id uuid NOT NULL,
    tag_id uuid NOT NULL,
    CONSTRAINT fk_task_id FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_tag_id FOREIGN KEY (tag_id) REFERENCES tag(id),
    UNIQUE (task_id, tag_id)
);
