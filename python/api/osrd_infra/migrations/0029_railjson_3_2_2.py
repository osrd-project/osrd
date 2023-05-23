# Generated by Django 4.1.5 on 2023-05-22 10:09

import django.db.models.deletion
import osrd_schemas.infra
from django.db import migrations, models

import osrd_infra.utils


class Migration(migrations.Migration):

    dependencies = [
        ("osrd_infra", "0028_rollingstock_energy_sources"),
    ]

    operations = [
        migrations.AlterField(
            model_name="infra",
            name="railjson_version",
            field=models.CharField(default="3.3.0", editable=False, max_length=16),
        ),
        migrations.RunSQL(
            sql=[("UPDATE osrd_infra_infra SET railjson_version = '3.3.0'")],
            reverse_sql=[("UPDATE osrd_infra_infra SET railjson_version = '3.2.1'")],
        ),
        migrations.CreateModel(
            name="DeadSectionModel",
            fields=[
                (
                    "id",
                    models.BigAutoField(
                        auto_created=True,
                        primary_key=True,
                        serialize=False,
                        verbose_name="ID",
                    ),
                ),
                ("obj_id", models.CharField(max_length=255)),
                (
                    "data",
                    models.JSONField(validators=[osrd_infra.utils.PydanticValidator(osrd_schemas.infra.DeadSection)]),
                ),
                (
                    "infra",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        to="osrd_infra.infra",
                    ),
                ),
            ],
            options={
                "verbose_name_plural": "dead sections",
                "unique_together": {("infra", "obj_id")},
            },
        ),
    ]
