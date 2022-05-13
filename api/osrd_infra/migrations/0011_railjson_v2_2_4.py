# Generated by Django 4.0.4 on 2022-05-13 12:57

from django.db import migrations, models


def apply_migration(apps, schema_editor):
    """Update infra from v2.2.3 to v2.2.4"""
    Infra = apps.get_model("osrd_infra", "Infra")
    updated_infra = []

    for infra in Infra.objects.all():
        if infra.railjson_version == "2.2.3":
            infra.railjson_version = "2.2.4"
            updated_infra.append(infra)
    Infra.objects.bulk_update(updated_infra, ["railjson_version"])


def revert_migration(apps, schema_editor):
    """Revert infra from v2.2.4 to v2.2.3"""
    Infra = apps.get_model("osrd_infra", "Infra")
    updated_infra = []

    for infra in Infra.objects.all():
        if infra.railjson_version == "2.2.4":
            infra.railjson_version = "2.2.3"
            updated_infra.append(infra)
    Infra.objects.bulk_update(updated_infra, ["railjson_version"])


class Migration(migrations.Migration):

    dependencies = [
        ("osrd_infra", "0010_bufferstoplayer"),
    ]

    operations = [
        migrations.AlterField(
            model_name="infra",
            name="railjson_version",
            field=models.CharField(default="2.2.4", editable=False, max_length=16),
        ),
        migrations.RunPython(code=apply_migration, reverse_code=revert_migration),
    ]
