(ns harja.domain.vesivaylat.toimenpide
  (:require [clojure.spec :as s]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.urakoitsija :as vv-urakoitsija]
            [harja.domain.vesivaylat.alus :as vv-alus]
            [harja.domain.vesivaylat.turvalaite :as vv-turvalaite]
            [harja.domain.vesivaylat.sopimus :as vv-sopimus]
            [harja.domain.vesivaylat.vayla :as vv-vayla]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(def
  ^{:doc "Reimarin työlajit."}
  reimari-tyolajit
  {"1022541807" :tukityot
   "1022541801" :kiinteat-turvalaitteet
   "1022541802" :poijut
   "1022541803" :viitat
   "1022541805" :muut-vaylatyot
   "1022541806" :rakennus-ja-kuljetuspalvelut
   "1022541808" :muut-palvelut
   "1022541804" :vesiliikennemerkit
   "1022540501" :kiintea-turvalaite})

(def
  ^{:doc "Reimarin työluokat. Huom: eri koodeilla voi olla sama selite."}
  reimari-tyoluokat
  {"1022541920" :kuljetuskaluston-huolto-ja-kunnossapito
   "1022541918" :toimistotyot
   "1022541901" :valo-ja-energialaitteet
   "1022541921" :turvalaitteiden-tukikohtatyot
   "1022541906" :asennus-ja-huolto
   "1022541909" :asennus-ja-huolto
   "1022541905" :valo-ja-energialaitteet
   "1022541919" :tukikohtatyot
   "1022541915" :tarkastustyot
   "1022541922" :muut-tukikohtatyot
   "1022541904" :ymparistotyot
   "1022541908" :valo-ja-energialaitteet
   "1022541903" :rakenteet
   "1022541914" :navi
   "1022541916" :rakennuspalvelut
   "1022541925" :muut-tyot
   "1022541917" :kuljetuspalvelut
   "1022541923" :vts-tyot
   "1022541911" :rakenteet
   "1022541913" :pienehkot-vaylatyot
   "1022541912" :ymparistotyot
   "1022541902" :telematiikkalaitteet
   "1022541924" :luotsitoiminnan-palvelut
   "1022541907" :telematiikkalaitteet
   "1022541910" :telematiikkalaitteet})

(def ^{:doc "Reimarin toimenpidetyypit."}
reimari-toimenpidetyypit
  {"1022542046" :alukset-ja-veneet
   "1022542040" :toimistotyot
   "1022542048" :muu-kuljetuskalusto
   "1022542001" :valo-ja-energialaitetyot
   "1022542050" :poiju-ja-viittakorjaustyot
   "1022542010" :sijaintitarkastus
   "1022542019" :sijaintitarkastus
   "1022542009" :asennus-tarkastus-ja-vaihto
   "1022542021" :siirto
   "1022542047" :autot-traktorit
   "1022542042" :T&K
   "1022542044" :kiinteistojen-yllapito-ja-huolto
   "1022542022" :vaihto
   "1022542020" :asennus
   "1022542033" :tarkastustyot
   "1022542053" :muut-tukityot
   "1022542045" :muun-kaluston-kunnossapito
   "1022542052" :muut-turvalaitetyot
   "1022542024" :ankkurointityot
   "1022542008" :raivaus-ja-ymparistonhoito
   "1022542041" :koulutus
   "1022542051" :valo-ja-energialaitetyot
   "1022542018" :asennus-tarkastus-ja-vaihto
   "1022542006" :peruskorjaus
   "1022542043" :varasto-ja-hankintatyot
   "1022542049" :ankkuripainojen-tyot
   "1022542032" :navityot
   "1022542034" :kiinteistot
   "1022542005" :tarkastus-huolto-ja-korjaus
   "1022542062" :huoltotyo
   "1022542060" :muut-palvelut
   "1022542025" :sijoittajatyot
   "1022542038" :aluskalustolla
   "1022542002" :sektorien-tarkastus-ja-saato
   "1022542039" :auto-tai-muulla-kalustolla
   "1022542061" :huoltotyo
   "1022542007" :uudisrakentaminen
   "1022542023" :poisto
   "1022542054" :VTS-tyot
   "1022542013" :vaihto
   "1022542011" :asennus
   "1022542003" :tutkamajakkatyot
   "1022542012" :siirto
   "1022542027" :tarkastus-huolto-ja-korjaus
   "1022542031" :kivien-ym-esteiden-poisto
   "1022540608" :Sektorin-tarkastus
   "1022542037" :uittorakenteet
   "1022542035" :satamarakenteet
   "1022542016" :sijoittajatyot
   "1022542030" :raivaus-ja-ymparistonhoito
   "1022542058" :pelastustoimintapalvelut
   "1022542029" :uudisrakentaminen
   "1022542036" :kanavarakenteet
   "1022542028" :peruskorjaus
   "1022542004" :kaukovalvontalaitetyot
   "1022542014" :poisto
   "1022542015" :ankkurointityot
   "1022542059" :jaanmurtopalvelut
   "1022542056" :tutkintoajo
   "1022542055" :luotsiajo
   "1022542017" :kaukovalvontalaitetyot
   "1022542026" :kaukovalvontalaitetyot})

(def ^{:doc "Reimarin toimenpiteen tilat"}
reimari-tilat
  {"1022541202" :suoritettu
   "1022541201" :suunniteltu
   "1022541203" :peruttu})

(define-tables
  ["reimari_toimenpide" ::toimenpide
   {"muokattu" ::m/muokattu
    "muokkaaja" ::m/muokkaaja-id
    "luotu" ::m/luotu
    "luoja" ::m/luoja-id
    "poistettu" ::m/poistettu?
    "poistaja" ::m/poistaja-id}])

(defn toimenpide-idlla [toimenpiteet id]
  (first (filter #(= (::id %) id) toimenpiteet)))

(defn toimenpiteet-tyolajilla [toimenpiteet tyolaji]
  (filter #(= (::tyolaji %) tyolaji) toimenpiteet))

(defn toimenpiteet-vaylalla [toimenpiteet vayla-id]
  (filter #(= (get-in % [::vayla ::vv-vayla/id]) vayla-id) toimenpiteet))

(def tyolajit (vals reimari-tyolajit))

(def tyolaji-fmt
  (merge
    (into {}
          (map (juxt identity
                     (comp
                       (fn [s] (clojure.string/replace s "-" " "))
                       clojure.string/capitalize
                       name))
               tyolajit))
    {:kiinteat-turvalaitteet "Kiinteät turvalaitteet"
     :kiintea-turvalaite "Kiinteä turvalaite"
     :tukityot "Tukityöt"
     :muut-vaylatyot "Muut väylätyöt"}))