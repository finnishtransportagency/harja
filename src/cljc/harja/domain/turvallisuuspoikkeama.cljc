(ns harja.domain.turvallisuuspoikkeama
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.set :as set]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]])
    [harja.domain.urakka :as ur]
    [harja.domain.kayttaja :as kayttaja]
    [harja.domain.muokkaustiedot :as muokkaustiedot])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["turvallisuuspoikkeama" ::turvallisuuspoikkeama
   ;; TODO Lisää uudelleennimeämisiä sitä mukaan kun tarvii (-id viittauksille jne.)
   {"urakka" ::urakka-id}])

(def turpo-tyypit
  {:tyotapaturma "Työtapaturma"
   :vaaratilanne "Vaaratilanne"
   :turvallisuushavainto "Turvallisuushavainto"
   :muu "Muu"})

(def vahinkoluokittelu-tyypit
  {:henkilovahinko "Henkilövahinko"
   :omaisuusvahinko "Omaisuusvahinko"
   :ymparistovahinko "Ympäristövahinko"})

(def turpo-vakavuusasteet
  {:lieva "Lievä"
   :vakava "Vakava"})

(def turpo-vaaralliset-aineet
  {:vaarallisten-aineiden-kuljetus "Mukana vaarallisten aineiden kuljetus"
   :vaarallisten-aineiden-vuoto "Vuoto tapahtunut"})

(def turpo-tyontekijan-ammatit
  {:aluksen_paallikko "Aluksen päällikkö"
   :asentaja "Asentaja"
   :asfalttityontekija "Asfalttityöntekijä"
   :harjoittelija "Harjoittelija"
   :hitsaaja "Hitsaaja"
   :kunnossapitotyontekija "Kunnossapitotyöntekijä"
   :kansimies "Kansimies"
   :kiskoilla_liikkuvan_tyokoneen_kuljettaja "Kiskoilla liikkuvan työkoneen kuljettaja"
   :konemies "Konemies"
   :kuorma-autonkuljettaja "Kuorma-auton kuljettaja"
   :liikenteenohjaaja "Liikenteenohjaaja"
   :mittamies "Mittamies"
   :panostaja "Panostaja"
   :peramies "Perämies"
   :porari "Porari"
   :rakennustyontekija "Rakennustyöntekijä"
   :ratatyontekija "Ratatyöntekijä"
   :ratatyosta_vastaava "Ratatyöstä vastaava"
   :sukeltaja "Sukeltaja"
   :sahkotoiden_ammattihenkilo "Sähkötöiden ammattihenkilö"
   :tilaajan_edustaja "Tilaajan edustaja"
   :turvalaiteasentaja "Turvalaiteasentaja"
   :turvamies "Turvamies"
   :tyokoneen_kuljettaja "Työkoneen kuljettaja"
   :tyonjohtaja "Työnjohtaja"
   :valvoja "Valvoja"
   :veneenkuljettaja "Veneenkuljettaja"
   :vaylanhoitaja "Väylänhoitaja"
   :muu_tyontekija "Muu työntekijä"
   :tyomaan_ulkopuolinen "Työmään ulkopuolinen"})

(defn kuvaile-tyontekijan-ammatti [turvallisuuspoikkeama]
  (if (and (= (:tyontekijanammatti turvallisuuspoikkeama) :muu_tyontekija)
           (not (str/blank? (:tyontekijanammattimuu turvallisuuspoikkeama))))
    (:tyontekijanammattimuu turvallisuuspoikkeama)
    (turpo-tyontekijan-ammatit
      (:tyontekijanammatti turvallisuuspoikkeama))))


(defn henkilovahingon-checkboksien-avaimet-jarjestyksessa [map]
  (-> (sort-by map (keys map))
      (->> (remove #{:ei_tietoa}))
      (conj :ei_tietoa)))

(def vammat
  {:haavat_ja_pinnalliset_vammat "Haavat ja pinnalliset vammat"
   :luunmurtumat "Luunmurtumat"
   :sijoiltaan_menot_nyrjahdykset_ja_venahdykset "Sijoiltaan menot nyrjähdykset ja venähdykset"
   :amputoitumiset_ja_irti_repeamiset "Amputoitumiset ja irti repeämiset (ruumiinosan menetys)"
   :tarahdykset_ja_sisaiset_vammat_ruhjevammat "Tärähdykset ja sisäiset vammat ruhjevammat"
   :palovammat_syopymat_ja_paleltumat "Palovammat syöpymät ja paleltumat"
   :myrkytykset_ja_tulehdukset "Myrkytykset ja tulehdukset"
   :hukkuminen_ja_tukehtuminen "Hukkuminen ja tukehtuminen"
   :aanen_ja_varahtelyn_vaikutukset "Äänen ja värähtelyn vaikutukset"
   :aarilampotilojen_valon_ja_sateilyn_vaikutukset "Äärilämpötilojen valon ja säteilyn vaikutukset"
   :sokki "Sokki"
   :useita_samantasoisia_vammoja "Useita samantasoisia vammoja"
   :muut "Muut"
   :ei_tietoa "Ei tietoa"})

(def vammat-avaimet-jarjestyksessa
  (henkilovahingon-checkboksien-avaimet-jarjestyksessa vammat))

(def vahingoittunut-ruumiinosa
  {:paan_alue "Pään alue (pl. silmät)"
   :silmat "Silmä(t)"
   :niska_ja_kaula "Niska ja kaula"
   :selka "Selkä"
   :vartalo "Vartalo, mukaan lukien sisäelimet"
   :sormi_kammen "Sormi (sormet), kämmen"
   :ranne "Ranne"
   :muu_kasi "Muu käsi, mukaan lukien olkapää"
   :nilkka "Nilkka"
   :jalkatera_ja_varvas "Jalkaterä ja varvas (varpaat)"
   :muu_jalka "Muu jalka, mukaan lukien lonkka ja nivuset"
   :koko_keho "Koko keho (useat kehon alueet)"
   :ei_tietoa "Ei tietoa"})

(def kuvaa-turpon-tila {:avoin "Avoin"
                        :kasitelty "Käsitelty"
                        :taydennetty "Täydennetty"
                        :suljettu "Suljettu"})

(def vahingoittunut-ruumiinosa-avaimet-jarjestyksessa
  (henkilovahingon-checkboksien-avaimet-jarjestyksessa vahingoittunut-ruumiinosa))

(def juurisyyt
  [:puutteelliset_henkilonsuojaimet
   :puutteelliset_tyovalineet_tai_koneet
   :puutteellinen_jarjestys_tai_siisteys
   :puutteellinen_patevyys_tai_kelpoisuus
   :puutteellinen_tai_puuttuva_ohjeistus_tai_perehdytys
   :ohjeiden_vastainen_toiminta_tai_riskinotto
   :tyomaan_ulkopuolinen_tekija_tai_olosuhde
   :muu])

(def juurisyyn-kuvaus
  {:puutteelliset_henkilonsuojaimet "Puutteelliset henkilönsuojaimet"
   :puutteelliset_tyovalineet_tai_koneet "Puutteelliset työvälineet tai -koneet"
   :puutteellinen_jarjestys_tai_siisteys "Puutteellinen järjestys tai siisteys"
   :puutteellinen_patevyys_tai_kelpoisuus "Puutteellinen pätevyys tai kelpoisuus"
   :puutteellinen_tai_puuttuva_ohjeistus_tai_perehdytys "Puutteellinen tai puuttuva ohjeistus tai perehdytys"
   :ohjeiden_vastainen_toiminta_tai_riskinotto "Ohjeiden vastainen toiminta tai riskinotto"
   :tyomaan_ulkopuolinen_tekija_tai_olosuhde "Työmaan ulkopuolinen tekijä tai olosuhde"
   :muu "Muu (avoin kohta)"})

(def juurisyyn-koodi
  {:puutteelliset_henkilonsuojaimet 0
   :puutteelliset_tyovalineet_tai_koneet 1
   :puutteellinen_jarjestys_tai_siisteys 2
   :puutteellinen_patevyys_tai_kelpoisuus 3
   :puutteellinen_tai_puuttuva_ohjeistus_tai_perehdytys 4
   :ohjeiden_vastainen_toiminta_tai_riskinotto 5
   :tyomaan_ulkopuolinen_tekija_tai_olosuhde 6
   :muu 7})
