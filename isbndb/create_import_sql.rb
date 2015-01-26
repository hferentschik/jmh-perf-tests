require 'json'

BOOK_INSERT_SQL = "insert into Book (id, isbn, title, publisher) values (%{book_id}, %{isbn}, '%{title}', '%{publisher}');\n"
AUTHOR_INSERT_SQL = "insert into Author (id, name) values (%{author_id}, '%{name}');\n"
BOOK_AUTHOR_SQL = "insert into Book_Author (Book_id, authors_id) values (%{book_id}, %{author_id});\n"

def get_author_id(author_string_id)
  if @author_id_to_int_id[author_string_id].nil?
    @author_id_to_int_id[author_string_id] = @author_id
  end
  @author_id_to_int_id[author_string_id]
end

@author_id = 0
@book_id = 0

@book_inserts = Array.new
@author_inserts = Array.new
@book_author_inserts = Array.new

@book_id_to_author_id = Hash.new
@author_id_to_int_id = Hash.new

json_file = ARGV[0]
if json_file.nil? || !File.exist?(json_file)
  abort("You need to specify an input file")
end
data = JSON.parse(IO.read(json_file))
data.each do |book|
  values = {:book_id => @book_id, :isbn => book['isbn13'], :title => book['title'].gsub("'", "''"), :publisher => book['publisher_text'].gsub("'", "''")  }
  @book_inserts << BOOK_INSERT_SQL % values
  @book_id += 1

  book['author_data'].each do |author|
    author_id = get_author_id author['id']
    if author_id == @author_id
      values = {:author_id => author_id, :name => author['name'].gsub("'", "''")}
      @author_inserts << AUTHOR_INSERT_SQL % values
      @author_id += 1
    end
    @book_id_to_author_id[@book_id] = author_id
  end
end

@book_id_to_author_id.each do |key, value|
  values = {:book_id => key, :author_id => value}
  @book_author_inserts << BOOK_AUTHOR_SQL % values
end

File.open('import.sql', 'w') do |f|
  f.puts @book_inserts
  f.puts @author_inserts
  f.puts @book_author_inserts
end







