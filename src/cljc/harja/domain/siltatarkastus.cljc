(ns harja.domain.siltatarkastus)

(defn siltatarkastuskohteen-tyyppi
  "Siltatarkastuksessa käytettyjen kohteiden tyyppi mäpättynä järjestysnumeroon"
  [kohdenro]
  (case kohdenro
    ;; Alusrakenne
    1 "maatukienSiisteysJaKunto"
    2 "valitukienSiisteysJaKunto"
    3 "laakeritasojenSiisteysJaKunto"
    ;; Päällysrakenne
    4 "kansilaatta"
    5 "paallysteenKunto"
    6 "reunapalkinSiisteysJaKunto"
    7 "reunapalkinLiikuntasauma"
    8 "reunapalkinJaPaallysteenValisenSaumanSiisteysJaKunto"
    9 "sillanpaidenSaumat"
    10 "sillanJaPenkereenRaja"
    ;; Varusteet ja laitteet
    11 "kaiteidenJaSuojaverkkojenVauriot"
    12 "liikuntasaumalaitteidenSiisteysJaKunto"
    13 "laakerit"
    14 "syoksytorvet"
    15 "tippuputket"
    16 "kosketussuojatJaNiidenKiinnitykset"
    17 "valaistuslaitteet"
    18 "johdotJaKaapelit"
    19 "liikennemerkit"
    ;; Siltapaikan rakenteet
    20 "kuivatuslaitteidenSiisteysJaKunto"
    21 "etuluiskienSiisteysJaKunto"
    22 "keilojenSiisteysJaKunto"
    23 "tieluiskienSiisteysJaKunto"
    24 "portaidenSiisteysJaKunto"
    "tuntematon"))

(defn siltatarkastuskohteen-nimi
  "Siltatarkastuksessa käytettyjen kohteiden nimet mäpättynä järjestysnumeroon"
  [kohdenro]
  (case kohdenro
    ;; Alusrakenne
    1 "Maatukien siisteys ja kunto"
    2 "Välitukien siisteys ja kunto"
    3 "Laakeritasojen siisteys ja kunto"
    ;; Päällysrakenne
    4 "Kansilaatta"
    5 "Päällysteen kunto"
    6 "Reunapalkin siisteys ja kunto"
    7 "Reunapalkin liikuntasauma"
    8 "Reunapalkin ja päällysteen välisen sauman siisteys ja kunto"
    9 "Sillanpäiden saumat"
    10 "Sillan ja penkereen raja"
    ;; Varusteet ja laitteet
    11 "Kaiteiden ja suojaverkkojen vauriot"
    12 "Liikuntasaumalaitteiden siisteys ja kunto"
    13 "Laakerit"
    14 "Syöksytorvet"
    15 "Tippuputket"
    16 "Kosketussuojat ja niiden kiinnitykset"
    17 "Valaistuslaitteet"
    18 "Johdot ja kaapelit"
    19 "Liikennemerkit"
    ;; Siltapaikan rakenteet
    20 "Kuivatuslaitteiden siisteys ja kunto"
    21 "Etuluiskien siisteys ja kunto"
    22 "Keilojen siisteys ja kunto"
    23 "Tieluiskien siisteys ja kunto"
    24 "Portaiden siisteys ja kunto"
    "Tuntematon tarkastuskohde"))

(defn kohde-id->paarakenneosa
  "Siltatarkastuksessa käytettyjen päärakenneosien tyyppi ja nimi mäpättynä kohde-id:hen"
  [id]
  (cond
    (<= id 3) {:tyyppi "alusrakenne" :nimi "Alusrakenne"}
    (<= id 10) {:tyyppi "paallysrakenne" :nimi "Päällysrakenne"}
    (<= id 19) {:tyyppi "varusteet-ja-laitteet" :nimi "Varusteet ja laitteet"}
    (<= id 24) {:tyyppi "siltapaikan-rakenteet" :nimi "Siltapaikan rakenteet"}
    :else {:tyyppi "tuntematon" :nimi "Tuntematon"}))

(defn koodi->arvo
  "Siltatarkastuksessa käytettyjen toimenpidetarvekoodien arvo mäpättynä koodiin"
  [koodi]
  (case koodi
    "A" "eiToimenpiteita"
    "B" "puhdistettava"
    "C" "urakanKunnostettava"
    "D" "korjausOhjelmoitava"
    "E" "E"
    "-" "eiPade"
    "eiToimenpiteita"))

(defn koodi->kuvaus
  "Siltatarkastuksessa käytettyjen toimenpidetarvekoodien kuvaus mäpättynä koodiin"
  [koodi]
  (case koodi
    "A" "Ei toimenpiteitä"
    "B" "Puhdistettava"
    "C" "Urakan kunnostettava"
    "D" "Korjaus ohjelmoitava"
    "E" "E"
    "-" "Ei päde"
    "Ei toimenpiteitä"))
