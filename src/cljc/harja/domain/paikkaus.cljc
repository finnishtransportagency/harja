(ns harja.domain.paikkaus
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.kyselyt.specql :as harja-specql]
    [harja.domain.tierekisteri :as tr]
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
  ["tr_osoite" ::tr/tr-osoite]
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
    "poistettu" ::muokkaustiedot/poistettu?}]
  ["paikkauskohde_tyomenetelma" ::paikkauskohde-tyomenetelma
   {"id" ::tyomenetelma-id
    "nimi" ::tyomenetelma-nimi
    "lyhenne" ::tyomenetelma-lyhenne}])

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
    ::pinta-ala
    ::lahde})

(def tienkohta-perustiedot
  #{::tienkohta-id
    ::ajorata
    ::kaista
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

;; VHAR-1384, huom. nämä lyhenteitä
(def paikkaustyomenetelmat-jotka-kiinnostaa-yhaa
  #{"UREM" "KTVA" "REPA" "SIPA" "SIPU"})

(defn
  pitaako-paikkauskohde-lahettaa-yhaan? [tyomenetelman-lyhenne]
  (boolean (paikkaustyomenetelmat-jotka-kiinnostaa-yhaa tyomenetelman-lyhenne)))

(defn fmt-tila [tila]
  (let [tila (if (= tila "hylatty") "hylätty" tila)]
    (str/capitalize tila)))

(def paikkauskohteiden-yksikot
  #{"m2" "t" "kpl" "jm"})

(defn id->tyomenetelma [id tyomenetelmat]
  (first (filter (fn [t]
                   (and (not (nil? t)) ;; Varmistetaan, että annettu työmenetelmä ei ole nil
                              (= id (::tyomenetelma-id t))))
                 tyomenetelmat)))


(defn tyomenetelma-id->nimi [id tyomenetelmat]
  (::tyomenetelma-nimi (id->tyomenetelma id tyomenetelmat)))

(defn tyomenetelma-id->lyhenne [id tyomenetelmat]
  (::tyomenetelma-lyhenne (id->tyomenetelma id tyomenetelmat)))

(defn tyomenetelma-id [nimi-tai-lyhenne tyomenetelmat]
  (::tyomenetelma-id (first (filter
             #(or (= nimi-tai-lyhenne (::tyomenetelma-nimi %)) (= nimi-tai-lyhenne (::tyomenetelma-lyhenne %)))
             tyomenetelmat))))

(defn levittimella-tehty? [kohde tyomenetelmat]
  (let [tyomenetelma (or (:tyomenetelma kohde)
                         (::tyomenetelma kohde))
        tyomenetelma (tyomenetelma-id->nimi tyomenetelma tyomenetelmat)]
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
   :lahde :harja.domain.paikkaus/lahde
   :kpl :harja.domain.paikkaus/kpl
   :juoksumetri :harja.domain.paikkaus/juoksumetri})

(def db-paikkaus->speqcl-avaimet
  {:f1 :harja.domain.paikkaus/id
   :f2 :harja.domain.paikkaus/alkuaika
   :f3 :harja.domain.paikkaus/loppuaika
   :f4 :harja.domain.tierekisteri/tie
   :f5 :harja.domain.tierekisteri/aosa,
   :f6 :harja.domain.tierekisteri/aet,
   :f7 :harja.domain.tierekisteri/losa,
   :f8 :harja.domain.tierekisteri/let,
   :f9 :harja.domain.paikkaus/tyomenetelma,
   :f10 :harja.domain.paikkaus/massatyyppi,
   :f11 :harja.domain.paikkaus/leveys,
   :f12 :harja.domain.paikkaus/raekoko,
   :f13 :harja.domain.paikkaus/kuulamylly,
   :f14 :harja.domain.paikkaus/sijainti,
   :f15 :harja.domain.paikkaus/massamaara,
   :f16 :harja.domain.paikkaus/massamenekki,
   :f17 :harja.domain.paikkaus/juoksumetri,
   :f18 :harja.domain.paikkaus/kpl,
   :f19 :harja.domain.paikkaus/pinta-ala,
   :f20 :harja.domain.paikkaus/lahde,
   :f21 :harja.domain.paikkaus/paikkauskohde-id,
   :f22 :harja.domain.paikkaus/nimi,
   :f23 :harja.domain.paikkaus/yksikko,
   :f24 :harja.domain.paikkaus/tienkohta-id,
   :f25 :harja.domain.paikkaus/ajorata,
   :f26 :harja.domain.paikkaus/reunat,
   :f27 :harja.domain.paikkaus/ajourat,
   :f28 :harja.domain.paikkaus/ajouravalit,
   :f29 :harja.domain.paikkaus/keskisaumat,
   :f30 :harja.domain.paikkaus/kaista})

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
   :harja.domain.paikkaus/lahde :lahde
   :harja.domain.paikkaus/kpl :kpl
   :harja.domain.paikkaus/juoksumetri :juoksumetri})

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
   :harja.domain.paikkaus/tiemerkintapvm :tiemerkintapvm})

(def paikkauskohde->specql-avaimet
  {:id :harja.domain.paikkaus/id
   :luoja-id ::muokkaustiedot/luoja-id
   :ulkoinen-id :harja.domain.paikkaus/ulkoinen-id
   :nimi :harja.domain.paikkaus/nimi
   :poistettu ::muokkaustiedot/poistettu?
   :luotu ::muokkaustiedot/luotu
   :muokkaaja-id ::muokkaustiedot/muokkaaja-id
   :muokattu ::muokkaustiedot/muokattu
   :urakka-id :harja.domain.paikkaus/urakka-id
   :yhalahetyksen-tila :harja.domain.paikkaus/yhalahetyksen-tila
   :virhe :harja.domain.paikkaus/virhe
   :tarkistettu :harja.domain.paikkaus/tarkistettu
   :tarkistaja-id :harja.domain.paikkaus/tarkistaja-id
   :ilmoitettu-virhe :harja.domain.paikkaus/ilmoitettu-virhe
   :alkupvm :harja.domain.paikkaus/alkupvm
   :loppupvm :harja.domain.paikkaus/loppupvm
   :tilattupvm :harja.domain.paikkaus/tilattupvm
   :tyomenetelma :harja.domain.paikkaus/tyomenetelma
   ::tierekisteriosoite_laajennettu :harja.domain.paikkaus/tierekisteriosoite_laajennettu
   :paikkauskohteen-tila :harja.domain.paikkaus/paikkauskohteen-tila
   :suunniteltu-maara :harja.domain.paikkaus/suunniteltu-maara
   :suunniteltu-hinta :harja.domain.paikkaus/suunniteltu-hinta
   :yksikko :harja.domain.paikkaus/yksikko
   :lisatiedot :harja.domain.paikkaus/lisatiedot
   :pot? :harja.domain.paikkaus/pot?
   :valmistumispvm :harja.domain.paikkaus/valmistumispvm
   :toteutunut-hinta :harja.domain.paikkaus/toteutunut-hinta
   :tiemerkintaa-tuhoutunut? :harja.domain.paikkaus/tiemerkintaa-tuhoutunut?
   :takuuaika :harja.domain.paikkaus/takuuaika
   :tiemerkintapvm :harja.domain.paikkaus/tiemerkintapvm})
