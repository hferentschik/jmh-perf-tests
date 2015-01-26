CREATE TABLE Book (
    id  INT UNSIGNED,
    isbn BIGINT UNSIGNED,
    title VARCHAR(255),
    publisher VARCHAR(255)
);

CREATE TABLE Author (
    id  INT UNSIGNED,
    name VARCHAR(255)
);

CREATE TABLE Book_Author (
    book_id  INT UNSIGNED,
    authors_id INT UNSIGNED
);
