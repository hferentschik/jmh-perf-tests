require 'yaml'
require 'rest-client'
require 'json'

# http://isbndb.com/api/v2/docs
# http://rubydoc.info/gems/rest-client/1.6.7/frames

# Some constants
FREE_USE_LIMIT=500
BASE_URL="http://isbndb.com/api/v2/json/%{current_key}/books"
QUERY_CONFIG_FILE='query_config.yml'
RESTCLIENT_LOG=STDOUT

# Instance variables (no need really to list them here. Just as reference)
@jobs
@keys
@current_key
@total_pages
@isbn_as_json

def load_job_description
  job_description_file = File.join(File.dirname(__FILE__), QUERY_CONFIG_FILE)
  unless File.exist?(job_description_file)
    abort("Required job description #{job_description_file} does not exist")
  end
  @jobs = YAML::load(File.open(job_description_file))
end

def save_job_description
  File.open(File.join(File.dirname(__FILE__), QUERY_CONFIG_FILE), 'w') { |f| f.write @jobs.to_yaml }
end

def load_api_keys
  key_file_name = ENV['HOME']+'/.isbndb.yml'
  unless File.exist?(key_file_name)
    abort("Required key file #{key_file_name} does not exist")
  end
  @keys = YAML::load(File.open(key_file_name))
end

def set_next_key
  if @current_key.nil?
    @current_key = @keys.values[0]
  else
    i = 0
    @keys.values.each do |key|
      i += 1
      break if key == @current_key
    end
    if @keys.values[i].nil?
      abort("no more keys")
    else
      @current_key = @keys.values[i]
    end
  end
  puts "Using API key '#{@current_key}'"
end

def execute_query(query, index='combined', page=1)
  begin
    values = {:current_key => @current_key}
    url = BASE_URL % values
    RestClient.get(url, {:params => {:q => query, :i => index, :p => page, :opt => 'keystats'}}) { |response, request, result, &block|
      case response.code
        when 200
          json_response = JSON.parse(response)
          @total_pages = json_response['page_count']
          request_count = json_response['keystats']['member_use_requests']
          if request_count > FREE_USE_LIMIT
            set_next_key
          end
          puts "(#{request_count}) retrieving page #{page} of #{@total_pages}"
          json_response['data'].each do |book|
            @isbn_as_json << book
          end
        else
          response.return!(request, result, &block)
      end
    }
  rescue => e
    puts e
  end
end

def open_json(out_file_name)
  file_name = File.join(File.dirname(__FILE__), out_file_name)
  if File.exist?(file_name)
    @isbn_as_json = JSON.parse(IO.read(file_name))
  else
    @isbn_as_json = Array.new
  end
end

def write_json(out_file_name)
  File.open(File.join(File.dirname(__FILE__), out_file_name), "w") do |f|
    f.write(@isbn_as_json.to_json)
  end
end

########################################################################################################################
# Program start here
########################################################################################################################

load_api_keys
set_next_key
load_job_description
@jobs['jobs'].each do |job|
  puts "Processing job: #{job['name']}"
  open_json job['out']
  if job['status'] == 'completed'
    puts " '#{job['name']}' completed"
    next
  end
  page = job['pages_downloaded'] + 1
  begin
    execute_query job['query']['q'], job['query']['i'], page
    job['pages_downloaded'] = page
    save_job_description
    page += 1
    write_json job['out']
  end while @total_pages.nil? || page <= @total_pages
  if page > @total_pages
    job['status'] == 'completed'
    save_job_description
  end
end


