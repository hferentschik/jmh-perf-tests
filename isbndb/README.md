Scripts for downloading book data from isbndb.com (JSON format) and 
then create a SQL import file.

#  Prerequisites

* Ruby 
* Bundler
* `bundle install`
* `.isbndb.yml` in `$HOME` directory listing at least one key for isbndb. Something like:

      key1: XXXXXXXX
      key2: YYYYYYYY


# isdndb.com download

1. Create a query config file:

        jobs:
          - name: Manning
            status: new
            pages_downloaded: 0
            query:
              q: Manning
              i: publisher_name
            out: manning-wesley.json
          - name: Addison-Wesley
            status: new
            pages_downloaded: 0
            query:
              q: Addison-Wesley
              i: publisher_name
            out: manning-wesley.json
   
2. Run: `bundle exec ruby ./isbndb-download.rb` 

# SQL generation

1. Create database schema:

        mysql -u username -p -h localhost DATA-BASE-NAME < schema.sql

2. `bundle exec ruby create_import_sql.rb manning-wesly.json`

