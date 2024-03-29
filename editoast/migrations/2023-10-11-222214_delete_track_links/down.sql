-- This file should undo anything in `up.sql`

CREATE TABLE infra_layer_track_section_link (
	id int8 PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY,
	obj_id varchar(255) NOT NULL,
	geographic geometry(point, 3857) NOT NULL,
	schematic geometry(point, 3857) NOT NULL,
	infra_id int8 NOT NULL REFERENCES infra(id) ON DELETE CASCADE,
	UNIQUE (infra_id, obj_id)
);
CREATE INDEX infra_layer_track_section_link_geographic ON infra_layer_track_section_link USING gist (geographic);
CREATE INDEX infra_layer_track_section_link_schematic ON infra_layer_track_section_link USING gist (schematic);

CREATE TABLE infra_object_track_section_link (
	id int8 PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY,
	obj_id varchar(255) NOT NULL,
	data jsonb NOT NULL,
	infra_id int8 NOT NULL REFERENCES infra(id) ON DELETE CASCADE,
	UNIQUE (infra_id, obj_id)
);
