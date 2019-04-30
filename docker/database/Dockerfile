FROM postgres:10

MAINTAINER napster@npstr.space

ENV POSTGRES_USER icu
COPY initdb.sh /usr/local/bin/
COPY run.sh /usr/local/bin/

HEALTHCHECK --retries=1 CMD /usr/bin/psql -U $POSTGRES_USER -tAc "SELECT 1 FROM pg_database WHERE datname='icu';" | grep -q 1 || exit 1

ENTRYPOINT ["/bin/bash", "run.sh"]
