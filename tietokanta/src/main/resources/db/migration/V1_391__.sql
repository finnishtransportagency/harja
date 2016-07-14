ALTER TABLE tyokonehavainto DROP CONSTRAINT tyokonehavainto_pkey;
ALTER TABLE tyokonehavainto ADD PRIMARY KEY (jarjestelma, tyokoneid);
