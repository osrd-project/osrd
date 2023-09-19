# Generated by Django 4.1.5 on 2023-03-17 10:55

from django.db import migrations

from osrd_infra.migrations import run_sql_create_infra_search_table


class Migration(migrations.Migration):
    dependencies = [
        ("osrd_infra", "0019_rename_type_study_study_type"),
    ]

    operations = [
        run_sql_create_infra_search_table(
            name="osrd_search_signal",
            source_table="osrd_infra_signalmodel",
            search_columns={
                "label": "{source}.data->'extensions'->'sncf'->>'label'",
                "line_name": "ts.data->'extensions'->'sncf'->>'line_name'",
            },
            extra_columns={
                "infra_id": ("{source}.infra_id", "INT"),
                "obj_id": ("{source}.obj_id", "VARCHAR(255)"),
                "aspects": (
                    """array_remove(
                        ARRAY(
                            SELECT jsonb_array_elements_text(jsonb_strip_nulls({source}.data)->'extensions'->'sncf'->'aspects')
                        )::text[],
                        NULL
                    )""",
                    "TEXT[]",
                ),
                "systems": (
                    "ARRAY(SELECT * FROM jsonb_to_recordset(jsonb_strip_nulls({source}.data)->'logical_signals') AS (signaling_system text))",
                    "TEXT[]",
                ),
                "line_code": ("(ts.data->'extensions'->'sncf'->>'line_code')::integer", "INT"),
            },
            triggers=True,
            phony_model_name="OsrdSearchSignal",
            joins="INNER JOIN osrd_infra_tracksectionmodel AS ts ON ts.infra_id = {source}.infra_id AND ts.obj_id = {source}.data->>'track'",
        ),
        migrations.RunSQL(
            sql="CREATE INDEX IF NOT EXISTS osrd_search_signal_infra_id_line_code_idx ON osrd_search_signal(infra_id, line_code) INCLUDE (label, aspects);",
            reverse_sql="DROP INDEX IF EXISTS osrd_search_signal_infra_id_line_code_idx",
        ),
    ]