# Fitness API image — used by docker-compose (local) and Render (cloud).
#
# Locally, docker-compose mounts ./fitness-api over /var/www/html so PHP
# edits are picked up live. On Render, the code copied in below is what
# runs.
FROM php:8.3-apache

# pdo_mysql for the database; GD (with JPEG) to re-encode avatar uploads.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libjpeg62-turbo-dev libpng-dev \
    && docker-php-ext-configure gd --with-jpeg \
    && docker-php-ext-install -j"$(nproc)" gd pdo_mysql \
    && rm -rf /var/lib/apt/lists/*

# Production PHP settings: errors go to the server log, never into an
# API response.
RUN mv "$PHP_INI_DIR/php.ini-production" "$PHP_INI_DIR/php.ini"

# mod_rewrite so the front-controller .htaccess can route /auth/login ->
# index.php, and allow .htaccess overrides inside the docroot.
RUN a2enmod rewrite
COPY docker/php/apache-override.conf /etc/apache2/conf-available/apache-override.conf
RUN a2enconf apache-override

# Listen on $PORT. Render sets it (10000 by default); everywhere else it
# stays 80. Apache substitutes ${PORT} from the environment at startup.
ENV PORT=80
RUN sed -ri 's/^Listen 80$/Listen ${PORT}/' /etc/apache2/ports.conf \
    && sed -ri 's/<VirtualHost \*:80>/<VirtualHost *:${PORT}>/' \
        /etc/apache2/sites-available/000-default.conf

# Aiven's CA certificate (public) so PDO can verify the cloud database.
# Only used when DB_SSL_CA points at it (see render.yaml).
COPY certs/aiven-ca.pem /etc/ssl/certs/aiven-ca.pem

COPY fitness-api/ /var/www/html/
WORKDIR /var/www/html
