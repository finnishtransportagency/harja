(ns harja.palvelin.integraatiot.api.tyokalut.virheet
  (:require [harja.domain.oikeudet :as oikeudet])
  (:use [slingshot.slingshot :only [throw+]]))

(def +invalidi-json+ ::invalidi-json)
(def +invalidi-xml+ ::invalidi-xml)
(def +viallinen-kutsu+ ::viallinen-kutsu)
(def +ei-hakutuloksia+ ::ei-hakutuloksia)
(def +sisainen-kasittelyvirhe+ ::sisainen-kasittelyvirhe)

;; Virhekoodit
(def +invalidi-json-koodi+ "invalidi-json")
(def +sisainen-kasittelyvirhe-koodi+ "sisainen-kasittelyvirhe")
(def +ulkoinen-kasittelyvirhe-koodi+ "ulkoinen-kasittelyvirhe")
(def +virheellinen-liite-koodi+ "virheellinen-liite")
(def +tuntematon-urakka-koodi+ "tuntematon-urakka")
(def +tuntematon-sopimus-koodi+ "tuntematon-sopimus")
(def +urakkaa-ei-loydy+ "urakkaa-ei-loydy")
(def +paivystajia-ei-loydy+ "paivystajia-ei-loydy")
(def +tuntematon-kayttaja-koodi+ "tuntematon-kayttaja")
(def +tuntematon-yllapitokohde+ "tuntematon-yllapitokohde")
(def +urakkaan-kuulumaton-yllapitokohde+ "urakkaan-kuulumaton-yllapitokohde")
(def +viallinen-yllapitokohteen-aikataulu+ "viallinen-yllapitokohteen-aikataulu")
(def +viallinen-yllapitokohteen-tai-alikohteen-sijainti+ "viallinen-yllapitokohteen-tai-alikohteen-sijainti")
(def +tietokanta-yhteys-poikki+ "tietokanta-yhteys-poikki")
(def +tuntematon-kustannussuunnitelma+ "tuntematon-kustannussuunnitelma")
(def +tuntematon-maksuera+ "tuntematon-maksuera")
(def +lukittu-yllapitokohde+ "lukittu-yllapitokohde")
(def +tuntematon-ely+ "tuntematon-ely")
(def +puuttuva-geometria-alueurakassa+ "tuntematon-ely")
(def +tieluvan-data-vaarin+ "tieluvan-data-vaarin")

;; Virhetyypit
(def +virheellinen-liite+ "virheellinen-liite")
(def +tuntematon-silta+ "tuntematon-silta")
(def +duplikaatti-siltatarkastus+ "duplikaatti-siltatarkastus")
(def +tuntematon-materiaali+ "tuntematon-materiaali")
(def +tuntematon-kayttaja+ "tuntematon-kayttaja")
(def +tyhja-vastaus+ "tyhja-vastaus")
(def +kayttajalla-puutteelliset-oikeudet+ "kayttajalla-puutteelliset-oikeudet")
(def +puutteelliset-parametrit+ "puutteelliset-parametrit")
(def +virheellinen-sijainti+ "virheellinen-sijainti")
(def +virheellinen-paivamaara+ "virheellinen-paivamaara")
(def +sopimusta-ei-loydy+ "sopimusta ei löydy")
(def +paallystysilmoitus-lukittu+ "paallystysilmoitus-lukittu")
(def +ominaisuus-ei-kaytossa+ "ominaisuus-ei-kaytossa")
(def +virhe-tietolajin-arvojen-kasittelyssa+ "virhe-tietolajin-arvojen-kasittelyssa")
(def +virhe-tietolajin-arvojen-versiossa+ "virhe-tietolajin-arvojen-versiossa")
(def +puutteellinen-paikkatietoaineisto+ "puutteellinen-paikkatietoaineisto")

(defn heita-poikkeus
  ([tyyppi virheet] (heita-poikkeus tyyppi virheet nil))
  ([tyyppi virheet parametrit]
   (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
   (throw+
     (let [virheet (if (map? virheet) [virheet] virheet)]
       (merge {:type tyyppi
               :virheet virheet}
              parametrit)))))

;; TODO tämä ei heitä IllegalArgumentException, vaikka pitäisi

(defn heita-viallinen-apikutsu-poikkeus
  ([virheet] (heita-viallinen-apikutsu-poikkeus virheet nil))
  ([virheet parametrit]
   (heita-poikkeus +viallinen-kutsu+ virheet)))

(defn heita-sisainen-kasittelyvirhe-poikkeus
  ([virheet] (heita-sisainen-kasittelyvirhe-poikkeus virheet nil))
  ([virheet parametrit]
   (heita-poikkeus +sisainen-kasittelyvirhe-koodi+ virheet parametrit)))

(defn heita-ulkoinen-kasittelyvirhe-poikkeus
  ([virheet] (heita-ulkoinen-kasittelyvirhe-poikkeus virheet nil))
  ([virheet parametrit]
   (heita-poikkeus +ulkoinen-kasittelyvirhe-koodi+ virheet)))

(defn heita-puutteelliset-parametrit-poikkeus
  ([virheet] (heita-puutteelliset-parametrit-poikkeus virheet nil))
  ([virheet parametrit]
   (heita-poikkeus +puutteelliset-parametrit+ virheet)))

(defn heita-ei-hakutuloksia-apikutsulle-poikkeus
  ([virheet] (heita-ei-hakutuloksia-apikutsulle-poikkeus virheet nil))
  ([virheet parametrit]
   (heita-poikkeus +ei-hakutuloksia+ virheet)))
