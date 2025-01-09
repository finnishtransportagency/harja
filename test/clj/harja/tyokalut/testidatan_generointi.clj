(ns harja.tyokalut.testidatan-generointi
  "Apureita testidatan generointiin."
  (:require [clojure.test :refer :all]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.urakka :as urakka]
            [harja.testi :refer :all]))

(defn luo-laatupoikkeama
  "Anna urakkaid, niin luodaan laatupoikkeama.
  Laatupoikkeamalla ei ole sanktiota. Jos kaipaat laajeampaa testidataa, niin
  tee se itse."
  [urakkaid]
  {:yllapitokohde nil
   :sijainti {:type :point
              :coordinates [382554.0523636384 6675978.549765582]}
   :kuvaus "Kuvaus"
   :aika #inst "2016-09-15T09:00:01.000-00:00"
   :tr {:alkuosa 1
        :numero 1
        :alkuetaisyys 1
        :loppuetaisyys 2
        :loppuosa 2}
   :urakka urakkaid
   :sanktiot nil
   :tekija :tilaaja
   :kohde "Kohde"})

(defn uusi-sanktio
  "Aika ei ole pakollinen parametri. Summa ja toimenpideinstanssi-id ovat."
  [aika summa urakkaspesifi-toimenpideinstanssi-id]
  {:perintapvm (or aika #inst "2016-09-15T09:00:01.000-00:00")
   :laji :A
   :tyyppi 12
   :summa summa
   :indeksi "MAKU 2015"
   :suorasanktio false
   :toimenpideinstanssi urakkaspesifi-toimenpideinstanssi-id
   :vakiofraasi nil})

(defn luo-sanktio-paatos
  "Aika tai perustelu eivät ole pakollisia parametrejä."
  [aika perustelu]
  {:paatos :sanktio
   :kasittelytapa :puhelin
   :kasittelyaika (or aika #inst "2016-09-15T09:00:01.000-00:00")
   :perustelu (or perustelu "Testi")})

(defn uusi-bonus
  "Jos tyyppiä ei anneta, luodaan asiakastyytyväisyysbonus."
  [summa urakka-id pvm toimenpideinstanssi-id sopimus-id toteuman-lisatieto tyyppi]
  {:urakka-id urakka-id
   :pvm pvm
   :laskutuskuukausi pvm
   :rahasumma summa
   :indeksin_nimi "MAKU 2005"
   :toimenpideinstanssi toimenpideinstanssi-id
   :sopimus sopimus-id
   :tyyppi (or tyyppi "asiakastyytyvaisyysbonus")
   :lisatieto toteuman-lisatieto})

(defn uusi-tavoitehinnan-muutos
  "Omaa sukua tavoitehinnan oikaisu."
  [urakka-id hoitokauden-alkuvuosi summa selite]
  {::urakka/id urakka-id
   ::valikatselmus/otsikko "Oikaisu"
   ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
   ::valikatselmus/summa summa
   ::valikatselmus/selite (or selite "Rankka talvi. Suolamäärien vuoksi tavoitehintaa vähän nostettiin.")})

(defn uusi-paatos-tavoitehinnan-ylitys [urakka-id hoitokauden-alkuvuosi tilaajan-maksu urakoitsijan-maksu]
  {::urakka/id urakka-id
   ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
   ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
   ::valikatselmus/tilaajan-maksu tilaajan-maksu
   ::valikatselmus/urakoitsijan-maksu urakoitsijan-maksu})
