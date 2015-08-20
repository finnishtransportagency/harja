CREATE TABLE paikkausilmoitus_kommentti (
  ilmoitus integer REFERENCES paallystysilmoitus (id),
  kommentti integer REFERENCES kommentti (id)
);