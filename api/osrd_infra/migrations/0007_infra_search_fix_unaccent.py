# Generated by Django 4.1.5 on 2023-02-10 16:54

from django.db import migrations


class Migration(migrations.Migration):

    dependencies = [
        ("osrd_infra", "0006_operationalstudy_project_remove_timetable_infra_and_more"),
    ]

    operations = [
        migrations.RunSQL(
            sql="""
            CREATE OR REPLACE FUNCTION osrd_prepare_for_search(input_text text)
                RETURNS text
                LANGUAGE 'sql'
                IMMUTABLE PARALLEL SAFE 
            AS $BODY$
                SELECT array_to_string(
                        tsvector_to_array(
                            to_tsvector('simple', unaccent(coalesce(input_text, '')))
                        ),
                        E'\\n'
                    )
            $BODY$;
            CREATE OR REPLACE FUNCTION osrd_to_ilike_search(query text)
                RETURNS text
                LANGUAGE 'sql'
                IMMUTABLE PARALLEL SAFE 
            AS $BODY$
                SELECT '%' || array_to_string(
                        tsvector_to_array(
                            to_tsvector(
                                'simple',
                                unaccent(
                                    replace(
                                        coalesce(query, ''),
                                        '-',
                                        ' '
                                    )
                                )
                            )
                        ),
                        '%'
                    ) || '%'
            $BODY$;
            """,
            reverse_sql="""
            DROP FUNCTION IF EXISTS osrd_prepare_for_search;
            DROP FUNCTION IF EXISTS osrd_to_like_search;
            """,
        )
    ]
