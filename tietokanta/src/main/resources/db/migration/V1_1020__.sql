ALTER TABLE paikkauskohde ADD COLUMN urem_kok_massamaara NUMERIC(8, 3) DEFAULT NULL;
COMMENT ON COLUMN paikkauskohde.urem_kok_massamaara is 'UREM-paikkauskohteille voidaan tuoda Excelillä koko kohteen kokonaismassamäärä, mikä on syytä ottaa kantaan asti talteen.';
