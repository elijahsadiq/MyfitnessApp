FROM php:8.3-apache

# pdo_mysql for PDO prepared statements against MySQL.
# mod_rewrite so the front-controller .htaccess can route /auth/login ->
# index.php.
RUN docker-php-ext-install pdo_mysql \
    && a2enmod rewrite

# Allow .htaccess overrides inside the docroot without editing the shipped
# apache2.conf directly (safer under future base-image upgrades).
COPY apache-override.conf /etc/apache2/conf-available/apache-override.conf
RUN a2enconf apache-override

# Match Apache's default document root so `docker compose exec php ls
# /var/www/html` always shows the mounted API code.
WORKDIR /var/www/html
