# Generated by Django 3.2 on 2021-05-07 12:56

from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('osrd_infra', '0008_signal_applicable_direction'),
    ]

    operations = [
        migrations.CreateModel(
            name='AspectEntity',
            fields=[
            ],
            options={
                'verbose_name_plural': 'aspects',
                'proxy': True,
                'indexes': [],
                'constraints': [],
            },
            bases=('osrd_infra.entity',),
        ),
        migrations.CreateModel(
            name='BufferStopEntity',
            fields=[
            ],
            options={
                'verbose_name_plural': 'buffer stop entities',
                'proxy': True,
                'indexes': [],
                'constraints': [],
            },
            bases=('osrd_infra.entity',),
        ),
        migrations.CreateModel(
            name='DetectorEntity',
            fields=[
            ],
            options={
                'verbose_name_plural': 'detector entities',
                'proxy': True,
                'indexes': [],
                'constraints': [],
            },
            bases=('osrd_infra.entity',),
        ),
        migrations.CreateModel(
            name='RouteEntity',
            fields=[
            ],
            options={
                'verbose_name_plural': 'routes',
                'proxy': True,
                'indexes': [],
                'constraints': [],
            },
            bases=('osrd_infra.entity',),
        ),
        migrations.CreateModel(
            name='ScriptFunctionEntity',
            fields=[
            ],
            options={
                'verbose_name_plural': 'script functions',
                'proxy': True,
                'indexes': [],
                'constraints': [],
            },
            bases=('osrd_infra.entity',),
        ),
        migrations.CreateModel(
            name='SpeedSectionEntity',
            fields=[
            ],
            options={
                'verbose_name_plural': 'speed section entities',
                'proxy': True,
                'indexes': [],
                'constraints': [],
            },
            bases=('osrd_infra.entity',),
        ),
        migrations.CreateModel(
            name='SpeedSectionPartEntity',
            fields=[
            ],
            options={
                'verbose_name_plural': 'speed section part entities',
                'proxy': True,
                'indexes': [],
                'constraints': [],
            },
            bases=('osrd_infra.entity',),
        ),
        migrations.CreateModel(
            name='TVDSectionEntity',
            fields=[
            ],
            options={
                'verbose_name_plural': 'TVD section entities',
                'proxy': True,
                'indexes': [],
                'constraints': [],
            },
            bases=('osrd_infra.entity',),
        ),
        migrations.AlterField(
            model_name='applicabledirectioncomponent',
            name='applicable_direction',
            field=models.IntegerField(choices=[(0, 'Normal'), (1, 'Reverse'), (2, 'Both')]),
        ),
        migrations.AlterField(
            model_name='applicabledirectioncomponent',
            name='entity',
            field=models.OneToOneField(on_delete=django.db.models.deletion.CASCADE, related_name='applicable_direction', to='osrd_infra.entity'),
        ),
        migrations.CreateModel(
            name='SwitchPositionComponent',
            fields=[
                ('component_id', models.BigAutoField(primary_key=True, serialize=False)),
                ('position', models.IntegerField(choices=[(0, 'Left'), (1, 'Right')])),
                ('entity', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='switch_position_set', to='osrd_infra.entity')),
                ('switch', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, to='osrd_infra.switchentity')),
            ],
            options={
                'abstract': False,
            },
        ),
        migrations.CreateModel(
            name='SpeedSectionPartComponent',
            fields=[
                ('component_id', models.BigAutoField(primary_key=True, serialize=False)),
                ('entity', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='speed_section_part_set', to='osrd_infra.entity')),
                ('speed_section', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, to='osrd_infra.speedsectionentity')),
            ],
            options={
                'abstract': False,
            },
        ),
        migrations.CreateModel(
            name='SpeedSectionComponent',
            fields=[
                ('component_id', models.BigAutoField(primary_key=True, serialize=False)),
                ('speed', models.FloatField()),
                ('is_signalized', models.BooleanField()),
                ('entity', models.OneToOneField(on_delete=django.db.models.deletion.CASCADE, related_name='speed_section', to='osrd_infra.entity')),
            ],
            options={
                'default_related_name': 'speed_section_set',
            },
        ),
        migrations.CreateModel(
            name='SightDistanceComponent',
            fields=[
                ('component_id', models.BigAutoField(primary_key=True, serialize=False)),
                ('distance', models.FloatField()),
                ('entity', models.OneToOneField(on_delete=django.db.models.deletion.CASCADE, related_name='sight_distance', to='osrd_infra.entity')),
            ],
            options={
                'default_related_name': 'sight_distance_set',
            },
        ),
        migrations.CreateModel(
            name='ReleaseGroupComponent',
            fields=[
                ('component_id', models.BigAutoField(primary_key=True, serialize=False)),
                ('entity', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='release_group_set', to='osrd_infra.entity')),
                ('tvd_sections', models.ManyToManyField(to='osrd_infra.TVDSectionEntity')),
            ],
            options={
                'abstract': False,
            },
        ),
        migrations.CreateModel(
            name='RailScriptComponent',
            fields=[
                ('component_id', models.BigAutoField(primary_key=True, serialize=False)),
                ('script', models.JSONField()),
                ('entity', models.OneToOneField(on_delete=django.db.models.deletion.CASCADE, related_name='rail_script', to='osrd_infra.entity')),
            ],
            options={
                'abstract': False,
            },
        ),
        migrations.CreateModel(
            name='BerthingComponent',
            fields=[
                ('component_id', models.BigAutoField(primary_key=True, serialize=False)),
                ('is_berthing', models.BooleanField()),
                ('entity', models.OneToOneField(on_delete=django.db.models.deletion.CASCADE, related_name='berthing', to='osrd_infra.entity')),
            ],
            options={
                'abstract': False,
            },
        ),
        migrations.CreateModel(
            name='BelongsToTVDSectionComponent',
            fields=[
                ('component_id', models.BigAutoField(primary_key=True, serialize=False)),
                ('entity', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='belong_to_tvd_section_set', to='osrd_infra.entity')),
                ('tvd_section', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='tvd_section_components', to='osrd_infra.tvdsectionentity')),
            ],
            options={
                'abstract': False,
            },
        ),
        migrations.CreateModel(
            name='AspectConstraintComponent',
            fields=[
                ('component_id', models.BigAutoField(primary_key=True, serialize=False)),
                ('constraint', models.JSONField()),
                ('entity', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='constraint_set', to='osrd_infra.entity')),
            ],
            options={
                'abstract': False,
            },
        ),
        migrations.AddConstraint(
            model_name='speedsectioncomponent',
            constraint=models.CheckConstraint(check=models.Q(speed__gte=0), name='speed__gte=0'),
        ),
        migrations.AddConstraint(
            model_name='sightdistancecomponent',
            constraint=models.CheckConstraint(check=models.Q(distance__gte=0), name='distance__gte=0'),
        ),
    ]
