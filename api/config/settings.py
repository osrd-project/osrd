"""
Django settings for osrd project.

Generated by 'django-admin startproject' using Django 3.1.6.

For more information on this file, see
https://docs.djangoproject.com/en/3.1/topics/settings/

For the full list of settings and their values, see
https://docs.djangoproject.com/en/3.1/ref/settings/
"""
import os
from importlib.util import find_spec
from pathlib import Path

# Build paths inside the project like this: BASE_DIR / 'subdir'.
BASE_DIR = Path(__file__).resolve().parent.parent


# Quick-start development settings - unsuitable for production
# See https://docs.djangoproject.com/en/3.1/howto/deployment/checklist/

# SECURITY WARNING: keep the secret key used in production secret!
SECRET_KEY = 'BeZPeRmxngJAbJECmraxvpvLQebOYPNACjqGwujizGcIGHuEIz'


OSRD_INFRA_SRID = 4326


# Application definition

INSTALLED_APPS = [
    # django
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'django.contrib.gis',

    # vendor
    'rest_framework',
    'rest_framework_gis',

    # osrd apps
    'osrd.apps.MainServiceAppConfig',
    'osrd_infra.apps.OsrdInfraConfig',
]

MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
    'config.request_logger.RequestLogger',
]

REST_FRAMEWORK = {
    'DATETIME_FORMAT': '%Y-%m-%d %H:%M:%S.%f',
    'DEFAULT_PAGINATION_CLASS': 'rest_framework.pagination.PageNumberPagination',
    'PAGE_SIZE': 100,
}

ROOT_URLCONF = 'config.urls'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

WSGI_APPLICATION = 'config.wsgi.application'


# A postgis database is required
DATABASES = {}


# Password validation
# https://docs.djangoproject.com/en/3.1/ref/settings/#auth-password-validators

AUTH_PASSWORD_VALIDATORS = [
    {
        'NAME': 'django.contrib.auth.password_validation.UserAttributeSimilarityValidator',
    },
    {
        'NAME': 'django.contrib.auth.password_validation.MinimumLengthValidator',
    },
    {
        'NAME': 'django.contrib.auth.password_validation.CommonPasswordValidator',
    },
    {
        'NAME': 'django.contrib.auth.password_validation.NumericPasswordValidator',
    },
]


# Internationalization
# https://docs.djangoproject.com/en/3.1/topics/i18n/

LANGUAGE_CODE = 'en-us'

TIME_ZONE = 'UTC'

USE_I18N = True

USE_L10N = True

USE_TZ = True


# Static files (CSS, JavaScript, Images)
# https://docs.djangoproject.com/en/3.1/howto/static-files/

ROOT_PATH = 'osrd'

STATIC_URL = '/static/'

APPEND_SLASH = False

WORKSPACE = False


def getenv_bool(var_name, default=False):
    env_var = os.getenv(var_name, "")
    if not env_var:
        return default
    return env_var.lower() in ("1", "true")


OSRD_DEV_SETUP = getenv_bool("OSRD_DEV_SETUP")
OSRD_DEBUG = getenv_bool("OSRD_DEBUG", default=OSRD_DEV_SETUP)
OSRD_DEBUG_TOOLBAR = getenv_bool("OSRD_DEBUG_TOOLBAR", default=False)
OSRD_SKIP_AUTH = getenv_bool("OSRD_SKIP_AUTH", default=OSRD_DEBUG)


if OSRD_DEV_SETUP:
    ALLOWED_HOSTS = ["*"]
    INTERNAL_IPS = ['127.0.0.1']
else:
    ALLOWED_HOSTS = []


# SECURITY WARNING: don't run with debug turned on in production!
DEBUG = OSRD_DEBUG

# enable django debug toolbar if it is installed
if OSRD_DEBUG_TOOLBAR and find_spec("debug_toolbar") is not None:
    INSTALLED_APPS += ['debug_toolbar']
    MIDDLEWARE += ['debug_toolbar.middleware.DebugToolbarMiddleware']
    DEBUG_TOOLBAR_PANELS = [
        'debug_toolbar.panels.history.HistoryPanel',
        'debug_toolbar.panels.versions.VersionsPanel',
        'debug_toolbar.panels.timer.TimerPanel',
        'debug_toolbar.panels.settings.SettingsPanel',
        'debug_toolbar.panels.headers.HeadersPanel',
        'debug_toolbar.panels.request.RequestPanel',
        'debug_toolbar.panels.sql.SQLPanel',
        'debug_toolbar.panels.staticfiles.StaticFilesPanel',
        'debug_toolbar.panels.templates.TemplatesPanel',
        'debug_toolbar.panels.cache.CachePanel',
        'debug_toolbar.panels.signals.SignalsPanel',
        'debug_toolbar.panels.logging.LoggingPanel',
        'debug_toolbar.panels.redirects.RedirectsPanel',
        'debug_toolbar.panels.profiling.ProfilingPanel',
    ]

if OSRD_SKIP_AUTH:
    MIDDLEWARE += ['config.test_middleware.LocalUserMiddleware']
    REST_FRAMEWORK['DEFAULT_AUTHENTICATION_CLASSES'] = ['config.test_middleware.TestGatewayAuth']

logging_handlers = {}
for handler in os.getenv("LOGGERS", "").split(","):
    handler_name = handler
    handler_level = "DEBUG"

    split_handler = handler.split(":")
    if len(split_handler) == 2:
        handler_name, handler_level = split_handler
    logging_handlers[handler_name] = {
        'level': handler_level.upper(),
        'handlers': ['console'],
    }


LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'formatters': {
        'console': {
            'format': '{asctime} {levelname} {message}',
            'style': '{',
        },
    },
    'handlers': {
        'console': {
            'level': 'DEBUG',
            'class': 'logging.StreamHandler',
            'formatter': 'console',
        }
    },
    'loggers': logging_handlers,
}

OSRD_BACKEND_URL = os.getenv("OSRD_BACKEND_URL", "http://localhost:8080")
OSRD_BACKEND_TOKEN = os.getenv("OSRD_BACKEND_TOKEN", "")

CACHE_TIMEOUT = 60 * 60 * 48  # 48 hours
