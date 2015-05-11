package org.hibernate.search.test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

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

import static org.junit.Assert.assertEquals;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 1)
@Measurement(iterations = 2)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class SearchFacetingPerformance {
	private static final int BATCH_SIZE = 25;
	private static final String NATIVE_LUCENE_INDEX_DIR = "target/native-lucene";
	private static final String HSEARCH_LUCENE_INDEX_DIR = "target/hsearch-lucene";
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

	@Benchmark
	@SuppressWarnings("unused")
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

	@Benchmark
	@SuppressWarnings("unused")
	public void luceneFaceting() throws Exception {
		SortedSetDocValuesReaderState docValuesReaderState =
				new DefaultSortedSetDocValuesReaderState( searcher.getIndexReader() );
		FacetsCollector facetsCollector = new FacetsCollector();

		searcher.search( new MatchAllDocsQuery(), facetsCollector );

		// get facet results
		SortedSetDocValuesFacetCounts facets = new SortedSetDocValuesFacetCounts(
				docValuesReaderState, facetsCollector
		);
		FacetResult topFacetResult = facets.getTopChildren( 10, "authors.name_untokenized" );

		assertEquals(
				"Wrong facet ",
				"Bittinger, Marvin L.",
				topFacetResult.labelValues[0].label
		);
		assertEquals(
				"Wrong facet value count",
				169,
				(int) topFacetResult.labelValues[0].value
		);
	}

	// for testing in the IDE
	public static void main(String[] args) throws Exception {
		Options opt = new OptionsBuilder()
				.include( ".*" + SearchFacetingPerformance.class.getSimpleName() + ".*" )
				.build();
		new Runner( opt ).run();
	}
//
//	public static void main(String args[]) {
//		SearchFacetingPerformance performance = new SearchFacetingPerformance();
//		try {
//			performance.setUp();
//			performance.hsearchFaceting();
//			performance.luceneFaceting();
//			performance.tearDown();
//		}
//		catch ( Exception e ) {
//			System.err.println( e.getMessage() );
//			e.printStackTrace();
//		}
//	}


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
		cfg.setProperty( Environment.URL, "jdbc:mysql://localhost/books" );
		cfg.setProperty( Environment.USER, "hibernate" );
		cfg.setProperty( Environment.PASS, "hibernate" );

//		cfg.setProperty( Environment.HBM2DDL_AUTO, "create" );
		cfg.setProperty( Environment.SHOW_SQL, "false" );
		cfg.setProperty( Environment.FORMAT_SQL, "false" );

		// Search config
		cfg.setProperty( "hibernate.search.lucene_version", Version.LUCENE_4_10_3.toString() );
		cfg.setProperty( "hibernate.search.default.directory_provider", "filesystem" );
		cfg.setProperty( "hibernate.search.default.indexBase", HSEARCH_LUCENE_INDEX_DIR );
		cfg.setProperty( org.hibernate.search.cfg.Environment.ANALYZER_CLASS, StopAnalyzer.class.getName() );
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
		// create the Document
		Document document = new Document();

		// add the standard fields
		document.add( new TextField( "title", book.getTitle(), Field.Store.NO ) );
		document.add( new StringField( "isbn", book.getIsbn(), Field.Store.NO ) );
		document.add( new StringField( "publisher", book.getPublisher(), Field.Store.NO ) );

		// add the dynamic facet fields
		FacetsConfig config = new FacetsConfig();
		config.setMultiValued( "authors.name", true );

		for ( Author author : book.getAuthors() ) {
			String name = author.getName();
			document.add( new TextField( "authors.name", name, Field.Store.NO ) );
			document.add( new SortedSetDocValuesFacetField( "authors.name_untokenized", name ) );
		}
		writer.addDocument( config.build( document ) );
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
		Analyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig iwc = new IndexWriterConfig( Version.LUCENE_4_10_3, analyzer );

		if ( create ) {
			// Create a new index in the directory, removing any
			// previously indexed documents:
			iwc.setOpenMode( IndexWriterConfig.OpenMode.CREATE );
		}

		IndexWriter writer = new IndexWriter( dir, iwc );
		writer.commit();
		writer.close();
	}

	private IndexWriter getIndexWriter() throws Exception {
		File indexDirFile = new File( NATIVE_LUCENE_INDEX_DIR );
		Directory dir = FSDirectory.open( indexDirFile );
		Analyzer analyzer = new StandardAnalyzer( );
		IndexWriterConfig iwc = new IndexWriterConfig( Version.LUCENE_4_10_3, analyzer );
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
