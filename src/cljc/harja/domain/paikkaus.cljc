(ns harja.domain.paikkaus
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.kyselyt.specql :as harja-specql]
    [harja.pvm :as pvm]

    #?@(:clj  [
               [harja.kyselyt.specql-db :refer [define-tables]]
               ]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]]))
  #?(:clj
     (:import (org.postgis PGgeometry))))

(define-tables
  ["tr_osoite_laajennettu" ::tr-osoite-laajennettu]
  ["paikkauskohde" ::paikkauskohde
   {"luoja-id" ::muokkaustiedot/luoja-id
    "luotu" ::muokkaustiedot/luotu
    "muokkaaja-id" ::muokkaustiedot/muokkaaja-id
    "muokattu" ::muokkaustiedot/muokattu
    "poistettu" ::muokkaustiedot/poistettu?
    "tarkistettu" ::tarkistettu
    "tarkistaja-id" ::tarkistaja-id
    "ilmoitettu-virhe" ::ilmoitettu-virhe
    ::paikkaukset (specql.rel/has-many ::id
                                       ::paikkaus
                                       ::paikkauskohde-id)
    ::kustannukset (specql.rel/has-many ::id
                                        ::paikkaustoteuma
                                        ::paikkauskohde-id)}]
  ["paikkaus" ::paikkaus
   {"luoja-id" ::muokkaustiedot/luoja-id
    "luotu" ::muokkaustiedot/luotu
    "muokkaaja-id" ::muokkaustiedot/muokkaaja-id
    "muokattu" ::muokkaustiedot/muokattu
    "poistaja-id" ::muokkaustiedot/poistaja-id
    "poistettu" ::muokkaustiedot/poistettu?
    ::paikkauskohde (specql.rel/has-one ::paikkauskohde-id
                                        ::paikkauskohde
                                        ::id)
    ::tienkohdat (specql.rel/has-many ::id
                                      ::paikkauksen-tienkohta
                                      ::paikkaus-id)
    ::materiaalit (specql.rel/has-many ::id
                                       ::paikkauksen_materiaali
                                       ::paikkaus-id)}
   #?(:clj {::sijainti (specql.transform/transform (harja.kyselyt.specql/->GeometryTierekisteri))})]
  ["paikkauksen_tienkohta" ::paikkauksen-tienkohta
   {"id" ::tienkohta-id}]
  ["paikkauksen_materiaali" ::paikkauksen_materiaali
   {"id" ::materiaali-id}]
  ["paikkaustoteuma" ::paikkaustoteuma
   {"luoja-id" ::muokkaustiedot/luoja-id
    "luotu" ::muokkaustiedot/luotu
    "muokkaaja-id" ::muokkaustiedot/muokkaaja-id
    "muokattu" ::muokkaustiedot/muokattu
    "poistaja-id" ::muokkaustiedot/poistaja-id
    "poistettu" ::muokkaustiedot/poistettu?}])

(def paikkauskohteen-perustiedot
  #{::id
    ::ulkoinen-id
    ::nimi
    ::yhalahetyksen-tila
    ::paikkauskohteen-tila
    ::alkupvm
    ::loppupvm
    ::virhe
    ::tyomenetelma
    ::tarkistettu
    ::tarkistaja-id
    ::ilmoitettu-virhe
    ::yksikko})

(def paikkauksen-perustiedot
  #{::id
    ::urakka-id
    ::paikkauskohde-id
    ::ulkoinen-id
    ::alkuaika
    ::loppuaika
    ::tierekisteriosoite
    ::tyomenetelma
    ::massatyyppi
    ::leveys
    ::massamenekki
    ::raekoko
    ::kuulamylly
    ::sijainti
    ::massamaara
    ::lahde})

(def tienkohta-perustiedot
  #{::tienkohta-id
    ::ajorata
    ::reunat
    ::ajourat
    ::ajouravalit
    ::keskisaumat})

(def materiaalit-perustiedot
  #{::materiaali-id
    ::esiintyma
    ::kuulamylly-arvo
    ::muotoarvo
    ::sideainetyyppi
    ::pitoisuus
    ::lisa-aineet})

(def paikkaustoteuman-perustiedot
  #{::id
    ::urakka-id
    ::paikkauskohde-id
    ::ulkoinen-id
    ::toteuma-id
    ::kirjattu
    ::tyyppi
    ::selite
    ::hinta
    ::tyomenetelma
    ::valmistumispvm})

(s/def ::pvm (s/nilable (s/or :pvm pvm/pvm?
                              :date #(instance? #?(:cljs js/Date
                                                   :clj  java.util.Date) %))))

(s/def ::aikavali (s/nilable (s/coll-of ::pvm :kind? vector :count 2)))
(s/def ::paikkaus-idt (s/nilable (s/coll-of integer? :kind set?)))
(s/def ::tr (s/nilable map?))
(s/def ::tyomenetelmat (s/nilable set?))
(s/def ::ensimmainen-haku? boolean?)
(s/def ::teiden-pituudet (s/nilable map?))


(s/def ::urakan-paikkauskohteet-kysely (s/keys :req [::urakka-id]
                                               :opt-un [::aikavali ::paikkaus-idt ::tr ::tyomenetelmat ::ensimmainen-haku?]))

(s/def ::urakan-paikkauskohteet-vastaus (s/keys :req-un [::paikkaukset]
                                                :opt-un [::paikkauskohteet ::teiden-pituudet ::tyomenetelmat]))

(s/def ::paikkausurakan-kustannukset-kysely (s/keys :req [::urakka-id]
                                                    :opt-un [::aikavali ::paikkaus-idt ::tr ::tyomenetelmat ::ensimmainen-haku?]))

(s/def ::paikkausurakan-kustannukset-vastaus (s/keys :req-un [::kustannukset]
                                                     :opt-un [::paikkauskohteet ::tyomenetelmat]))

;; FIXME: keksitty lista. Hommaa YHA-jengiltä oikea lista
(def tyomenetelmat-jotka-lahetetaan-yhaan
  #{"massapintaus" "remix-pintaus"})

(defn pitaako-paikkauskohde-lahettaa-yhaan? [tyomenetelma]
  (boolean (tyomenetelmat-jotka-lahetetaan-yhaan tyomenetelma)))

(defn fmt-tila [tila]
  (let [tila (if (= tila "hylatty") "hylätty" tila)]
    (str/capitalize tila)))

;; Osa työmenetelmistä tallennetaan kantaan pelkällä lyhenteellä, tällä funktiolla saadaan niille selkokielinen selitys.
(defn kuvaile-tyomenetelma [tm]
  (case tm
    "KTVA" "KT-valuasfalttipaikkaus (KTVA)"
    "REPA" "Konetiivistetty reikävaluasfalttipaikkaus (REPA)"
    "SIPU" "Sirotepuhalluspaikkaus (SIPU)"
    "SIPA" "Sirotepintauksena tehty lappupaikkaus (SIPA)"
    "UREM" "Urapaikkaus (UREM/RREM)"
    "HJYR" "Jyrsintäkorjaukset (HJYR/TJYR)"
    tm))

(defn lyhenna-tyomenetelma [tm]
  (case tm
    "KT-valuasfalttipaikkaus (KTVA)" "KTVA"
    "Konetiivistetty reikävaluasfalttipaikkaus (REPA)" "REPA"
    "Sirotepuhalluspaikkaus (SIPU)" "SIPU"
    "Sirotepintauksena tehty lappupaikkaus (SIPA)" "SIPA"
    "Urapaikkaus (UREM/RREM)" "UREM"
    "Jyrsintäkorjaukset (HJYR/TJYR)" "HJYR"
    tm))

(def paikkauskohteiden-tyomenetelmat
  ["AB-paikkaus levittäjällä" "PAB-paikkaus levittäjällä" "SMA-paikkaus levittäjällä" "KTVA" "REPA" "SIPU" "SIPA" "UREM"
   "HJYR" "Kannukaatosaumaus" "Avarrussaumaus" "Sillan kannen päällysteen päätysauman korjaukset"
   "Reunapalkin ja päällysteen välisen sauman tiivistäminen" "Reunapalkin liikuntasauman tiivistäminen"
   "Käsin tehtävät paikkaukset pikapaikkausmassalla" "AB-paikkaus käsin" "PAB-paikkaus käsin"
   "Muu päällysteiden paikkaustyö"])

(def paikkauskohteiden-yksikot
  #{"m2" "t" "kpl" "jm"})

(defn urapaikkaus? [kohde]
  (let [tyomenetelma (or (:tyomenetelma kohde)
                         (::tyomenetelma kohde))] 
    (or (= "UREM" tyomenetelma)
        (= (kuvaile-tyomenetelma "UREM") tyomenetelma))))

(defn levittimella-tehty? [kohde]
  (let [tyomenetelma (or (:tyomenetelma kohde)
                         (::tyomenetelma kohde))]
    (or (= "AB-paikkaus levittäjällä" tyomenetelma)
       (= "PAB-paikkaus levittäjällä" tyomenetelma)
       (= "SMA-paikkaus levittäjällä" tyomenetelma))))

(def paikkaus->speqcl-avaimet
  {:id :harja.domain.paikkaus/id
   :luotu ::muokkaustiedot/luotu
   :urakka-id :harja.domain.paikkaus/urakka-id
   :paikkauskohde-id :harja.domain.paikkaus/paikkauskohde-id
   :ulkoinen-id :harja.domain.paikkaus/ulkoinen-id
   :alkuaika :harja.domain.paikkaus/alkuaika
   :loppuaika :harja.domain.paikkaus/loppuaika
   :tierekisteriosoite :harja.domain.paikkaus/tierekisteriosoite
   :tyomenetelma :harja.domain.paikkaus/tyomenetelma
   :massatyyppi :harja.domain.paikkaus/massatyyppi
   :leveys :harja.domain.paikkaus/leveys
   :massamenekki :harja.domain.paikkaus/massamenekki
   :raekoko :harja.domain.paikkaus/raekoko
   :kuulamylly :harja.domain.paikkaus/kuulamylly
   :massamaara :harja.domain.paikkaus/massamaara
   :pinta-ala :harja.domain.paikkaus/pinta-ala
   :sijainti :harja.domain.paikkaus/sijainti
   :lahde :harja.domain.paikkaus/lahde})


(def speqcl-avaimet->paikkaus
  {:harja.domain.paikkaus/id :id
   ::muokkaustiedot/luotu :luotu
   :harja.domain.paikkaus/urakka-id :urakka-id
   :harja.domain.paikkaus/paikkauskohde-id :paikkauskohde-id
   :harja.domain.paikkaus/ulkoinen-id :ulkoinen-id
   :harja.domain.paikkaus/alkuaika :alkuaika
   :harja.domain.paikkaus/loppuaika :loppuaika
   :harja.domain.paikkaus/tierekisteriosoite :tierekisteriosoite
   :harja.domain.paikkaus/tyomenetelma :tyomenetelma
   :harja.domain.paikkaus/massatyyppi :massatyyppi
   :harja.domain.paikkaus/leveys :leveys
   :harja.domain.paikkaus/massamenekki :massamenekki
   :harja.domain.paikkaus/raekoko :raekoko
   :harja.domain.paikkaus/kuulamylly :kuulamylly
   :harja.domain.paikkaus/massamaara :massamaara
   :harja.domain.paikkaus/pinta-ala :pinta-ala
   :harja.domain.paikkaus/sijainti :sijainti
   :harja.domain.paikkaus/lahde :lahde})

(def speqcl-avaimet->tierekisteri
  {:harja.domain.tierekisteri/aosa :aosa
   :harja.domain.tierekisteri/tie :tie
   :harja.domain.tierekisteri/aet :aet
   :harja.domain.tierekisteri/losa :losa
   :harja.domain.tierekisteri/let :let
   :harja.domain.tierekisteri/ajorata :ajorata})


(def specql-avaimet->paikkauskohde
  {:harja.domain.paikkaus/id :id
   ::muokkaustiedot/luoja-id :luoja-id
   :harja.domain.paikkaus/ulkoinen-id :ulkoinen-id
   :harja.domain.paikkaus/nimi :nimi
   ::muokkaustiedot/poistettu? :poistettu
   ::muokkaustiedot/luotu :luotu
   ::muokkaustiedot/muokkaaja-id :muokkaaja-id
   ::muokkaustiedot/muokattu :muokattu
   :harja.domain.paikkaus/urakka-id :urakka-id
   :harja.domain.paikkaus/yhalahetyksen-tila :yhalahetyksen-tila
   :harja.domain.paikkaus/virhe :virhe
   :harja.domain.paikkaus/tarkistettu :tarkistettu
   :harja.domain.paikkaus/tarkistaja-id :tarkistaja-id
   :harja.domain.paikkaus/ilmoitettu-virhe :ilmoitettu-virhe
   :harja.domain.paikkaus/alkupvm :alkupvm
   :harja.domain.paikkaus/loppupvm :loppupvm
   :harja.domain.paikkaus/tilattupvm :tilattupvm
   :harja.domain.paikkaus/tyomenetelma :tyomenetelma
   :harja.domain.paikkaus/tierekisteriosoite_laajennettu ::tierekisteriosoite_laajennettu
   :harja.domain.paikkaus/paikkauskohteen-tila :paikkauskohteen-tila
   :harja.domain.paikkaus/suunniteltu-maara :suunniteltu-maara
   :harja.domain.paikkaus/suunniteltu-hinta :suunniteltu-hinta
   :harja.domain.paikkaus/yksikko :yksikko
   :harja.domain.paikkaus/lisatiedot :lisatiedot
   :harja.domain.paikkaus/pot? :pot?
   :harja.domain.paikkaus/valmistumispvm :valmistumispvm
   :harja.domain.paikkaus/toteutunut-hinta :toteutunut-hinta
   :harja.domain.paikkaus/tiemerkintaa-tuhoutunut? :tiemerkintaa-tuhoutunut?
   :harja.domain.paikkaus/takuuaika :takuuaika
   :harja.domain.paikkaus/tiemerkintapvm :tiemerkintapvm
   })