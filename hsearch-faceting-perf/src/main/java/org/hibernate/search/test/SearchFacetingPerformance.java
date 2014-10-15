package org.hibernate.search.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.params.CategoryListParams;
import org.apache.lucene.facet.params.FacetIndexingParams;
import org.apache.lucene.facet.params.FacetSearchParams;
import org.apache.lucene.facet.search.CountFacetRequest;
import org.apache.lucene.facet.search.FacetRequest;
import org.apache.lucene.facet.search.FacetResult;
import org.apache.lucene.facet.search.FacetsCollector;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesAccumulator;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetFields;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.facet.taxonomy.CategoryPath;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import org.hibernate.CacheMode;
import org.hibernate.FlushMode;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.hibernate.search.query.facet.Facet;
import org.hibernate.search.query.facet.FacetSortOrder;
import org.hibernate.search.query.facet.FacetingRequest;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = SECONDS)
@State(Scope.Benchmark)
@Fork(10)
@Threads(Threads.MAX)
public class SearchFacetingPerformance {
	private static final int BATCH_SIZE = 25;
	private static final String NATIVE_LUCENE_INDEX_DIR = "native-lucene";
	private static final String HSEARCH_LUCENE_INDEX_DIR = "hsearch-lucene";
	private static final String AUTHOR_NAME_FACET = "authorNameFacet";
	private SessionFactory sessionFactory;
	private IndexSearcher searcher;

	@Setup
	public void setUp() throws Exception {
		Configuration configuration = buildConfiguration();
		sessionFactory = configuration.buildSessionFactory();
		if ( needsIndexing() ) {
			createNativeLuceneIndex();
			indexTestData();
		}
		searcher = getIndexSearcher();
	}

	@TearDown
	public void tearDown() {
		sessionFactory.close();
	}

	@GenerateMicroBenchmark
	public void hsearchFaceting() {
		FullTextSession fullTextSession = Search.getFullTextSession( sessionFactory.openSession() );

		FullTextQuery fullTextQuery = fullTextSession.createFullTextQuery( new MatchAllDocsQuery(), Book.class );

		QueryBuilder builder = fullTextSession.getSearchFactory().buildQueryBuilder().forEntity( Book.class ).get();
		FacetingRequest facetReq = builder.facet()
				.name( AUTHOR_NAME_FACET )
				.onField( "authors.name_untokenized" )
				.discrete()
				.orderedBy( FacetSortOrder.COUNT_DESC )
				.includeZeroCounts( false )
				.maxFacetCount( 10 )
				.createFacetingRequest();

		List<Facet> facets = fullTextQuery.getFacetManager().enableFaceting( facetReq ).getFacets( AUTHOR_NAME_FACET );
		assertEquals( "Wrong facet count", 10, facets.size() );
		assertEquals( "Wrong facet ", "Bittinger, Marvin L.", facets.get( 0 ).getValue() );
		assertEquals( "Wrong facet value count", 169, facets.get( 0 ).getCount() );

		fullTextSession.close();
	}

	@GenerateMicroBenchmark
	public void luceneFaceting() throws Exception {
		// setup the facets
		List<FacetRequest> facetRequests = new ArrayList<FacetRequest>();
		facetRequests.add( new CountFacetRequest( new CategoryPath( "authors.name_untokenized" ), 1 ) );
		FacetSearchParams facetSearchParams = new FacetSearchParams( new FacetIndexingParams(), facetRequests );
		SortedSetDocValuesReaderState state = new SortedSetDocValuesReaderState(
				new FacetIndexingParams( new CategoryListParams( "authors.name_untokenized" ) ),
				searcher.getIndexReader()
		);
		FacetsCollector facetsCollector = FacetsCollector.create(
				new SortedSetDocValuesAccumulator(
						state,
						facetSearchParams
				)
		);

		// search
		searcher.search( new MatchAllDocsQuery(), facetsCollector );

		// get the facet result
		List<FacetResult> facets = facetsCollector.getFacetResults();
		assertEquals( "Wrong facet count", 1, facets.size() );
		FacetResult topFacetResult = facets.get( 0 );

		assertEquals(
				"Wrong facet ",
				"Bittinger, Marvin L.",
				topFacetResult.getFacetResultNode().subResults.get( 0 ).label.components[1]
		);
		assertEquals(
				"Wrong facet value count",
				169,
				(int) topFacetResult.getFacetResultNode().subResults.get( 0 ).value
		);
	}

	// just for testing in the IDE
	public static void main(String args[]) {
		SearchFacetingPerformance performance = new SearchFacetingPerformance();
		try {
			performance.setUp();
			performance.hsearchFaceting();
			performance.luceneFaceting();
			performance.tearDown();
		}
		catch ( Exception e ) {
			System.err.println( e.getMessage() );
			e.printStackTrace();
		}
	}

	private Configuration buildConfiguration() {
		Configuration cfg = new Configuration();

		// ORM config
//		cfg.setProperty( Environment.DIALECT, "org.hibernate.dialect.H2Dialect" );
//		cfg.setProperty( Environment.DRIVER, "org.h2.Driver" );
//		cfg.setProperty( Environment.URL, "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1" );
//		cfg.setProperty( Environment.USER, "sa" );
//		cfg.setProperty( Environment.PASS, "" );

		cfg.setProperty( Environment.DIALECT, "org.hibernate.dialect.MySQL5InnoDBDialect" );
		cfg.setProperty( Environment.DRIVER, "com.mysql.jdbc.Driver" );
		cfg.setProperty( Environment.URL, "jdbc:mysql://localhost/hibernate" );
		cfg.setProperty( Environment.USER, "hibernate" );
		cfg.setProperty( Environment.PASS, "hibernate" );

//		cfg.setProperty( Environment.HBM2DDL_AUTO, "create" );
		cfg.setProperty( Environment.SHOW_SQL, "false" );
		cfg.setProperty( Environment.FORMAT_SQL, "false" );

		// Search config
		cfg.setProperty( "hibernate.search.lucene_version", Version.LUCENE_46.name() );
		cfg.setProperty( "hibernate.search.default.directory_provider", "filesystem" );
		cfg.setProperty( "hibernate.search.default.indexBase", HSEARCH_LUCENE_INDEX_DIR );
		cfg.setProperty( org.hibernate.search.Environment.ANALYZER_CLASS, StopAnalyzer.class.getName() );
		cfg.setProperty( "hibernate.search.default.indexwriter.merge_factor", "100" );
		cfg.setProperty( "hibernate.search.default.indexwriter.max_buffered_docs", "1000" );

		// configured classes
		cfg.addAnnotatedClass( Book.class );
		cfg.addAnnotatedClass( Author.class );

		return cfg;
	}

	private void indexTestData() throws Exception {
		IndexWriter indexWriter = getIndexWriter();

		FullTextSession fullTextSession = Search.getFullTextSession( sessionFactory.openSession() );
		fullTextSession.setFlushMode( FlushMode.MANUAL );
		fullTextSession.setCacheMode( CacheMode.IGNORE );
		Transaction transaction = fullTextSession.beginTransaction();
		ScrollableResults results = fullTextSession.createCriteria( Book.class )
				.setFetchSize( BATCH_SIZE )
				.scroll( ScrollMode.FORWARD_ONLY );
		int index = 0;
		while ( results.next() ) {
			index++;
			Book book = (Book) results.get( 0 );
			indexBookHSearch( fullTextSession, book );
			indexBookLucene( indexWriter, book );
			if ( index % BATCH_SIZE == 0 ) {
				fullTextSession.flushToIndexes();
				fullTextSession.clear();
			}
		}
		transaction.commit();
		fullTextSession.close();
	}

	private void indexBookHSearch(FullTextSession fullTextSession, Book book) {
		fullTextSession.index( book );
	}

	private void indexBookLucene(IndexWriter writer, Book book) throws Exception {
		Document document = new Document();
		document.add( new TextField( "title", book.getTitle(), Field.Store.NO ) );
		document.add( new StringField( "isbn", book.getIsbn(), Field.Store.NO ) );
		document.add( new StringField( "publisher", book.getPublisher(), Field.Store.NO ) );
		SortedSetDocValuesFacetFields dvFields = new SortedSetDocValuesFacetFields(
				new FacetIndexingParams(
						new CategoryListParams(
								"authors.name_untokenized"
						)
				)
		);
		List<CategoryPath> paths = new ArrayList<CategoryPath>();
		for ( Author author : book.getAuthors() ) {
			String name = author.getName();
			document.add( new TextField( "authors.name", name, Field.Store.NO ) );
			paths.add( new CategoryPath( "authors.name_untokenized", name ) );
		}
		if ( paths.size() > 0 ) {
			dvFields.addFields( document, paths );
		}
		writer.addDocument( document );
		writer.commit();
	}

	private boolean needsIndexing() {
		File nativeLuceneIndexDir = new File( NATIVE_LUCENE_INDEX_DIR );
		if ( !nativeLuceneIndexDir.exists() ) {
			return true;
		}

		File hsearchLuceneIndexDir = new File( HSEARCH_LUCENE_INDEX_DIR );
		if ( !hsearchLuceneIndexDir.exists() ) {
			return true;
		}

		return false;
	}

	private void createNativeLuceneIndex() throws Exception {
		boolean create = true;
		File indexDirFile = new File( NATIVE_LUCENE_INDEX_DIR );
		if ( indexDirFile.exists() && indexDirFile.isDirectory() ) {
			create = false;
		}

		Directory dir = FSDirectory.open( indexDirFile );
		Analyzer analyzer = new StandardAnalyzer( Version.LUCENE_46 );
		IndexWriterConfig iwc = new IndexWriterConfig( Version.LUCENE_46, analyzer );

		if ( create ) {
			// Create a new index in the directory, removing any
			// previously indexed documents:
			iwc.setOpenMode( IndexWriterConfig.OpenMode.CREATE );
		}

		IndexWriter writer = new IndexWriter( dir, iwc );
		writer.commit();
		writer.close( true );
	}

	private IndexWriter getIndexWriter() throws Exception {
		File indexDirFile = new File( NATIVE_LUCENE_INDEX_DIR );
		Directory dir = FSDirectory.open( indexDirFile );
		Analyzer analyzer = new StandardAnalyzer( Version.LUCENE_46 );
		IndexWriterConfig iwc = new IndexWriterConfig( Version.LUCENE_46, analyzer );
		iwc.setOpenMode( IndexWriterConfig.OpenMode.CREATE_OR_APPEND );
		return new IndexWriter( dir, iwc );
	}

	private IndexSearcher getIndexSearcher() {
		IndexReader indexReader;
		IndexSearcher indexSearcher = null;
		try {
			File indexDirFile = new File( NATIVE_LUCENE_INDEX_DIR );
			Directory dir = FSDirectory.open( indexDirFile );
			indexReader = DirectoryReader.open( dir );
			indexSearcher = new IndexSearcher( indexReader );
		}
		catch ( IOException ioe ) {
			ioe.printStackTrace();
		}

		return indexSearcher;
	}
}
