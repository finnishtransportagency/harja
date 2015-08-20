CREATE TABLE turvallisuuspoikkeama_kommentti (
  turvallisuuspoikkeama INTEGER REFERENCES turvallisuuspoikkeama (id),
  kommentti INTEGER REFERENCES kommentti (id)
);