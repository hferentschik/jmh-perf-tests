package org.hibernate.search.test;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.DocumentId;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Store;

@Entity
@Indexed
public class Book {

	@Id
	@GeneratedValue
	@DocumentId
	private Integer id;

	@Field(store = Store.YES)
	private String title;

	@Field(analyze = Analyze.NO, store = Store.YES)
	private String isbn;

	@Field(analyze = Analyze.NO, store = Store.YES)
	private String publisher;

	@ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@IndexedEmbedded
	private Set<Author> authors = new HashSet<Author>();

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getIsbn() {
		return isbn;
	}

	public void setIsbn(String isbn) {
		this.isbn = isbn;
	}

	public String getPublisher() {
		return publisher;
	}

	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}

	public Set<Author> getAuthors() {
		return authors;
	}

	public void setAuthors(Set<Author> authors) {
		this.authors = authors;
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}

		Book book = (Book) o;

		if ( !isbn.equals( book.isbn ) ) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		return isbn.hashCode();
	}

	@Override
	public String toString() {
		return "Book{" +
				"id=" + id +
				", title='" + title + '\'' +
				", isbn='" + isbn + '\'' +
				", publisher='" + publisher + '\'' +
				", authors=" + authors +
				'}';
	}
}



