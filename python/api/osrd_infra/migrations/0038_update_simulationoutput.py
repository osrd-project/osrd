# Generated by Django 4.1.5 on 2023-06-21 12:59

from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("osrd_infra", "0037_pathmodel_length"),
    ]

    operations = [
        migrations.RenameField(
            model_name="simulationoutput",
            old_name="electrification_conditions",
            new_name="electrification_ranges",
        ),
        migrations.AddField(
            model_name="simulationoutput",
            name="power_restriction_ranges",
            field=models.JSONField(default=list),
        ),
    ]
