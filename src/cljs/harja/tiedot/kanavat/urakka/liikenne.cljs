(ns harja.tiedot.kanavat.urakka.liikenne
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [cljs-time.core :as time]
            [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [namespacefy.core :refer [namespacefy]]

            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tyokalut.tuck :as tt]
            [harja.ui.viesti :as viesti]
            [harja.ui.modal :as modal]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.hallintayksikot :as hallintayksikot]
            [harja.tiedot.raportit :as raporttitiedot]

            [harja.domain.urakka :as ur]
            [harja.domain.sopimus :as sop]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-toiminto :as toiminto])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def tila (atom {:nakymassa? false
                 :liikennetapahtumien-haku-kaynnissa? false
                 :liikennetapahtumien-haku-tulee-olemaan-kaynnissa? false
                 :tallennus-kaynnissa? false
                 :valittu-liikennetapahtuma nil
                 :tapahtumarivit nil
                 :raporttiparametrit {}
                 :valinnat {:kayttajan-urakat '()
                            :aikavali nil
                            ::lt/kohde nil
                            ::lt-alus/suunta nil
                            ::lt-alus/aluslajit #{}
                            :niput? false}}))

(defn uusi-tapahtuma
  ([]
   (uusi-tapahtuma istunto/kayttaja u/valittu-sopimusnumero nav/valittu-urakka (pvm/nyt)))
  ([kayttaja sopimus urakka aika]
   {::lt/kuittaaja (namespacefy @kayttaja {:ns :harja.domain.kayttaja})
    ::lt/aika aika
    ::lt/sopimus {::sop/id (first @sopimus)
                  ::sop/nimi (second @sopimus)}
    ::lt/urakka {::ur/id (:id @urakka)}}))

(def valinnat
  (reaction
    (when (:nakymassa? @tila)
      {::ur/id (:id @nav/valittu-urakka)
       :aikavali @u/valittu-aikavali
       ::sop/id (first @u/valittu-sopimusnumero)})))

(def valintojen-avaimet
  [:kayttajan-urakat :aikavali ::lt/kohde
   ::lt-alus/suunta ::lt-alus/aluslajit :niput?
   ::toiminto/toimenpiteet ::lt-alus/nimi])

;; Yleiset
(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [uudet])
;; Haut
(defrecord HaeLiikennetapahtumat [])
(defrecord HaeLiikennetapahtumatKutsuLahetetty [])
(defrecord LiikennetapahtumatHaettu [tulos])
(defrecord LiikennetapahtumatEiHaettu [virhe])
(defrecord AsetaAloitusTiedot [aloitustiedot])
(defrecord KayttajanUrakatHaettu [urakat])
;; Lomake
(defrecord ValitseTapahtuma [tapahtuma])
(defrecord HaeEdellisetTiedot [tapahtuma])
(defrecord EdellisetTiedotHaettu [tulos])
(defrecord EdellisetTiedotEiHaettu [virhe])
(defrecord TapahtumaaMuokattu [tapahtuma])
(defrecord MuokkaaAluksia [alukset virheita?])
(defrecord VaihdaSuuntaa [alus suunta])
(defrecord AsetaSuunnat [suunta])
(defrecord TallennaLiikennetapahtuma [tapahtuma])
(defrecord TapahtumaTallennettu [tulos])
(defrecord TapahtumaEiTallennettu [virhe])
(defrecord SiirraKaikkiTapahtumaan [alukset])
(defrecord SiirraTapahtumaan [alus])
(defrecord SiirraTapahtumasta [alus])
(defrecord PoistaKetjutus [alus])
(defrecord KetjutusPoistettu [tulos id])
(defrecord KetjutusEiPoistettu [virhe id])

(defrecord UrakkaValittu [urakka valittu?])

(defn valinta-wrap [e! app polku]
  (r/wrap (get-in app [:valinnat polku])
          (fn [u]
            (e! (->PaivitaValinnat {polku u})))))

(defn hakuparametrit [app]
  ;; Ei nil arvoja
  (let [urakka-idt (into #{} (keep #(when (:valittu? %)
                                           (:id %))
                                        (get-in app [:valinnat :kayttajan-urakat])))]
    (into {} (filter val
                     (-> app :valinnat (dissoc :kayttajan-urakat) (assoc :urakka-idt urakka-idt))))))

(defn nakymaan [e! aloitustiedot]
  (e! (->AsetaAloitusTiedot aloitustiedot)))

(defn palvelumuoto->str [tapahtuma]
  (str/join ", "
            (into #{} (sort (map lt/fmt-palvelumuoto
                                 (filter ::toiminto/palvelumuoto
                                         (::lt/toiminnot tapahtuma)))))))

(defn toimenpide->str [tapahtuma]
  (str/join ", "
            (into #{} (sort (keep (comp lt/sulku-toimenpide->str
                                        ::toiminto/toimenpide)
                                  (::lt/toiminnot tapahtuma))))))

(defn silta-avattu? [tapahtuma]
  (boolean (some (comp (partial = :avaus) ::toiminto/toimenpide) (::lt/toiminnot tapahtuma))))

(defn tapahtumarivit [app tapahtuma]
  ;; Tapahtuma voi sisältää useita aluksia
  ;; Muunnetaan tapahtuvarivit per alus -muotoon.
  ;; Palvelin suodattaa tapahtumat aluksen nimellä, mutta tässä täytyy
  ;; vielä erikseen suodattaa alukset per rivi.
  (let [alus-nimi-suodatin (get-in app [:valinnat ::lt-alus/nimi])
        tapahtuman-alukset (::lt/alukset tapahtuma)
        alustiedot
        (map
          (fn [alus]
            (merge
              tapahtuma
              alus))
          (if (empty? alus-nimi-suodatin)
            tapahtuman-alukset
            (lt-alus/suodata-alukset-nimen-alulla
              tapahtuman-alukset
              (get-in app [:valinnat ::lt-alus/nimi]))))]

    ;; Alustiedot ovat tyhjiä jos rivi on vain itsepalveluiden kirjaamista.
    (if (empty? alustiedot)
      ;; Näytetään rivi, mikäli alus-suodatin ei ollut käytössä
      (when (empty? alus-nimi-suodatin)
        [tapahtuma])
      alustiedot)))

(defn koko-tapahtuma [rivi {:keys [haetut-tapahtumat]}]
  (some #(when (= (::lt/id rivi) (::lt/id %)) %) haetut-tapahtumat))

(defn hae-sulutuksen-suunta [tapahtuma haluttu-toiminto haluttu-suunta]
  ;; Palauttaa integerin montako suuntaa tapahtumalla 
  ;; Sulutuksen aluksilla on aina 1 suunta, tällä haetaan siis sulutuksen suunta
  (count (filter
   (fn [alus]
     (let [toiminnot #{(::toiminto/toimenpide (first (::lt/toiminnot tapahtuma)))
                       (::toiminto/toimenpide (second (::lt/toiminnot tapahtuma)))}]
       (and
        (contains? toiminnot haluttu-toiminto)
        (= (::lt-alus/suunta alus) haluttu-suunta))))
   (::lt/alukset tapahtuma))))

(defn laske-yhteenveto [tapahtumat haluttu-toiminto haluttu-suunta haluttu-palvelumuoto]
  ;; Laskee liikennetapahtumien yhteenvetotietoja
  ;; Eli käydään läpi ja lasketaan tapahtumat joilla parametrien mukainen toiminto/suunta/muoto
  ;; Palautetaan integer (Long) montako tapahtumaa löytyi
  (let [yhteensa (filter
                  (fn [tapahtuma]
                    (let [toiminto (::toiminto/toimenpide (first (::lt/toiminnot tapahtuma)))
                          palvelumuoto (::toiminto/palvelumuoto (first (::lt/toiminnot tapahtuma)))
                          suuntia-olemassa (hae-sulutuksen-suunta tapahtuma haluttu-toiminto haluttu-suunta)]

                      (or
                       ;; Lasketaan sulutuksia
                       (> suuntia-olemassa 0)

                       ;; Lasketaan annettua toimenpidettä 
                       (and
                        (= haluttu-suunta nil)
                        (= toiminto haluttu-toiminto))

                       ;; Lasketaan annettua palvelumuotoa
                       (and
                        (= haluttu-suunta nil)
                        (= haluttu-toiminto nil)
                        (= palvelumuoto haluttu-palvelumuoto))))) tapahtumat)]

    (count yhteensa)))

(defn tapahtumat-haettu [app tulos]
  (let [sulutukset-alas (laske-yhteenveto tulos :sulutus :alas nil)
        sulutukset-ylos (laske-yhteenveto tulos :sulutus :ylos nil)
        sillan-avaukset (laske-yhteenveto tulos :avaus nil nil)
        tyhjennykset (laske-yhteenveto tulos :tyhjennys nil nil)
        paikallispalvelut (laske-yhteenveto tulos nil nil :paikallis)
        kaukopalvelut (laske-yhteenveto tulos nil nil :kauko)
        itsepalvelut (laske-yhteenveto tulos nil nil :itse)
        muut (laske-yhteenveto tulos nil nil :muu)

        ;; Lasketaan toimenpiteet mitkä kuuluvat yhteenvetoon (kaikki paitsi :ei-avausta)
        tapahtumat-joilla-toimenpide (count (filter
                                             (fn [tapahtuma]
                                               ;; Käydään läpi tapahtumat, ja lasketaan ne joilla on toimenpide muu kuin ei-avausta
                                               (let [toimenpide (::toiminto/toimenpide (first (::lt/toiminnot tapahtuma)))]
                                                 (not= toimenpide :ei-avausta))) tulos))

        toimenpiteet {:sulutukset-ylos sulutukset-ylos
                      :sulutukset-alas sulutukset-alas
                      :sillan-avaukset sillan-avaukset
                      :tyhjennykset tyhjennykset
                      :yhteensa tapahtumat-joilla-toimenpide}

        palvelumuoto {:paikallispalvelu paikallispalvelut
                      :kaukopalvelu kaukopalvelut
                      :itsepalvelu itsepalvelut
                      :muu muut
                      :yhteensa (+
                                 paikallispalvelut
                                 kaukopalvelut
                                 itsepalvelut
                                 muut)}]

    (swap! lt/yhteenveto-atom assoc :toimenpiteet toimenpiteet)
    (swap! lt/yhteenveto-atom assoc :palvelumuoto palvelumuoto))

  (-> app
      (assoc :liikennetapahtumien-haku-kaynnissa? false)
      (assoc :liikennetapahtumien-haku-tulee-olemaan-kaynnissa? false)
      (assoc :haetut-tapahtumat tulos)
      (assoc :tapahtumarivit (mapcat #(tapahtumarivit app %) tulos))
      (assoc :raporttiparametrit
             (raporttitiedot/usean-urakan-raportin-parametrit
               (keep #(when (:valittu? %)
                        (:nimi %))
                     (:kayttajan-urakat (:valinnat app)))
               :kanavien-liikennetapahtumat
               {:alkupvm (first (:aikavali (:valinnat app)))
                :loppupvm (second (:aikavali (:valinnat app)))
                :urakkatyyppi :vesivayla-kanavien-hoito
                :yhteenveto @lt/yhteenveto-atom}))))

(defn tallennusparametrit [t]
  (-> t
      (assoc ::lt/kuittaaja-id (get-in t [::lt/kuittaaja ::kayttaja/id]))
      (assoc ::lt/kohde-id (get-in t [::lt/kohde ::kohde/id]))
      (assoc ::lt/urakka-id (:id @nav/valittu-urakka))
      (assoc ::lt/sopimus-id (get-in t [::lt/sopimus ::sop/id]))
      (update ::lt/alukset
              (fn [alukset] (map
                              (fn [alus]
                                (let [alus
                                      (-> alus
                                          (dissoc ::m/poistettu?)
                                          (set/rename-keys {:poistettu ::m/poistettu?})
                                          (dissoc :id)
                                          (dissoc :harja.ui.grid/virheet))]
                                  (->> (keys alus)
                                       (filter #(#{"harja.domain.kanavat.lt-alus"
                                                   "harja.domain.muokkaustiedot"}
                                                  (namespace %)))
                                       (select-keys alus))))
                              alukset)))
      (update ::lt/toiminnot
              (fn [toiminnot] (map (fn [toiminto]
                                     (->
                                       (->> (keys toiminto)
                                            (filter #(= (namespace %) "harja.domain.kanavat.lt-toiminto"))
                                            (select-keys toiminto))
                                       (update ::toiminto/palvelumuoto #(if (= :ei-avausta (::toiminto/toimenpide toiminto))
                                                                          nil
                                                                          %))
                                       (update ::toiminto/lkm #(cond (= :ei-avausta (::toiminto/toimenpide toiminto))
                                                                     nil

                                                                     (= :itse (::toiminto/palvelumuoto toiminto))
                                                                     %

                                                                     (nil? (::toiminto/palvelumuoto toiminto))
                                                                     nil

                                                                     :else 1))))
                                   toiminnot)))
      (dissoc :grid-virheita?
              :valittu-suunta
              ::lt/kuittaaja
              ::lt/kohde
              ::lt/urakka
              ::lt/sopimus)))

(defn voi-tallentaa? [t]
  (let [alukset (remove :poistettu (::lt/alukset t))]
    (boolean
     (and (not (:grid-virheita? t))
          (every? #(some? (::lt-alus/lkm %))
                  alukset)

         ; Jos toimenpide ei ole tyhjennys niin alus laji ei saa olla "Ei alusta" eikä nil
          (every? #(if
                    (not= (:arvo @lt/toimenpide-atom) :tyhjennys)
                     (and (not= (::lt-alus/laji %) :EI)
                          (not (nil? (::lt-alus/laji %))))
                     true)
                  alukset)

          (empty? (filter :koskematon (::lt/alukset t)))

          (every? #(and (some? (::lt-alus/suunta %))
                        (or (some? (::lt-alus/laji %))
                            (= (:arvo @lt/toimenpide-atom) :tyhjennys)))
                  alukset)
          (or
           (not-empty alukset)
           (every? #(or (= :itse (::toiminto/palvelumuoto %))
                        (= :ei-avausta (::toiminto/toimenpide %))) (::lt/toiminnot t)))))))

(defn sama-alusrivi? [a b]
  ;; Tunnistetaan muokkausgridin rivi joko aluksen id:llä, tai jos rivi on uusi, gridin sisäisellä id:llä
  (or
    (and
      (some? (::lt-alus/id a))
      (some? (::lt-alus/id b))
      (= (::lt-alus/id a)
         (::lt-alus/id b)))
    (and
      (some? (:id a))
      (some? (:id b))
      (= (:id a)
         (:id b)))))

(defn paivita-toiminnon-tiedot [tapahtuma toiminto]
  
  ;; Koska itsepalvelulla ja Tyhjennys toimenpiteellä halutaan suunta "ei määritelty",
  ;; jos käyttäjä vaihtaa toimenpidettä tai palvelumuotoa toiseen, korvaa suunnattomat alukset.
  (let [korvaa-suunnattomat-alukset (map (fn [alus]
                                           ;; Silloin kun suunnat-atomilla ei ole :ei-suuntaa avainta, 
                                           ;; tarkoittaa että toimenpide ei ole tyhjennys eikä palvelutyyppi ole itsepalvelu => korvataan suunnattomat alukset
                                           ;; Vaihdetaan arvo nilliksi niin käyttäjä huomaa korjata suunnan 
                                           (if (and (= (::lt-alus/suunta alus) :ei-suuntaa) 
                                                    (not (:ei-suuntaa @lt/suunnat-atom)))
                                             (assoc alus ::lt-alus/suunta nil)
                                             alus))
                                         (::lt/alukset tapahtuma))]
    (-> tapahtuma
        (assoc ::lt/toiminnot
               (mapcat val
                       (assoc
                        (group-by ::toiminto/kohteenosa-id (::lt/toiminnot tapahtuma))
                        ;; Etsi palvelumuoto kohteenosan id:llä, ja korvaa/luo arvo
                        (::toiminto/kohteenosa-id toiminto)
                        [toiminto])))

        ;; Korvaa suunnattomat alukset mikäli tarve
        (assoc ::lt/alukset korvaa-suunnattomat-alukset))))

(defn kohteenosatiedot-toimintoihin
  "Ottaa tapahtuman ja kohteen, ja yhdistää tapahtuman toimintoihin kohteen kohteenosien tiedot.
  Jos kyseessä on olemassaoleva tapahtuma, liitetään vanhoihin toiminto tietoihin mm. kohteenosan nimi.
  Jos kyseessä on uusi tapahtuma, luodaan tapahtumalle tyhjät toiminto tiedot, jotka täytetään loppuun lomakkeella."
  [tapahtuma kohde]
  (-> tapahtuma
      (assoc ::lt/kohde kohde)
      (update ::lt/toiminnot
              (fn [osat]
                (let [vanhat (group-by ::toiminto/kohteenosa-id osat)]
                  (map
                    (fn [osa]
                      (merge
                        (-> osa
                            (set/rename-keys {::osa/id ::toiminto/kohteenosa-id
                                              ::osa/kohde-id ::toiminto/kohde-id})
                            (assoc ::toiminto/lkm 1)
                            (assoc ::toiminto/palvelumuoto (::osa/oletuspalvelumuoto osa))
                            (assoc ::toiminto/toimenpide (if (osa/silta? osa)
                                                           :ei-avausta
                                                           :sulutus)))
                        (first (vanhat (::osa/id osa)))))
                    (::kohde/kohteenosat kohde)))))))

(defn tapahtuman-kohde-sisaltaa-sulun? [tapahtuma]
  (kohde/kohde-sisaltaa-sulun? (::lt/kohde tapahtuma)))

(defn aseta-suunta [rivi kohde]
  (if (kohde/kohde-sisaltaa-sulun? kohde)
    (assoc rivi :valittu-suunta nil)
    (assoc rivi :valittu-suunta :molemmat)))

(defn kasittele-suunta-alukselle [tapahtuma alukset]
  (map (fn [a]
         (let [valittu-suunta (#{:ylos :alas :ei-suuntaa} (:valittu-suunta tapahtuma))
               
               ;; Jos valittu-suunta on ei-suuntaa mutta sitä ei toimenpide tai palvelumuoto salli, vaihda suunta
               vaihdettu-suunta (if (and (not (:ei-suuntaa @lt/suunnat-atom))
                                         (= valittu-suunta :ei-suuntaa))
                                  :ylos
                                  valittu-suunta)
               
               tapahtumat (keep (fn [b]
                                    (if (sama-alusrivi? a b)
                                      b nil))
                                  (::lt/alukset tapahtuma))

               klikattu-suunta (::lt-alus/suunta (first tapahtumat))
               
               suunta (if (nil? klikattu-suunta)
                        vaihdettu-suunta
                        klikattu-suunta)]

           (assoc a ::lt-alus/suunta suunta)))
       alukset))

(defn poista-ketjutus [app alus-id]
  (let [poista-idlla (fn [alus-id alukset]
                       (remove (comp (partial = alus-id) ::lt-alus/id) alukset))]
    (-> app
        (update-in
          [:edelliset :ylos :edelliset-alukset]
          (partial poista-idlla alus-id))
        (update-in
         [:edelliset :ei-suuntaa :edelliset-alukset]
         (partial poista-idlla alus-id))
        (update-in
          [:edelliset :alas :edelliset-alukset]
          (partial poista-idlla alus-id)))))

(defn nayta-palvelumuoto? [osa]
  (boolean
    (not= (::toiminto/toimenpide osa) :ei-avausta)))

(defn nayta-itsepalvelut? [osa]
  (boolean
    (and (not= (::toiminto/toimenpide osa) :ei-avausta)
         (= (::toiminto/palvelumuoto osa) :itse))))

(defn suuntavalinta-str [edelliset suunta]
  (let [suunta->str (fn [suunta] (@lt/suunnat-atom suunta))]
    (str (suunta->str suunta)
         (when (suunta edelliset)
           (str ", " (count (get-in edelliset [suunta :edelliset-alukset])) " lähestyvää alusta")))))

(defn nayta-edelliset-alukset? [{:keys [valittu-liikennetapahtuma
                                        edellisten-haku-kaynnissa?
                                        edelliset]}]
  (boolean
    (and (::lt/kohde valittu-liikennetapahtuma)
         (not edellisten-haku-kaynnissa?)
         (or (:alas edelliset) (:ylos edelliset))
         (some? (:valittu-suunta valittu-liikennetapahtuma))
         (not (id-olemassa? (::lt/id valittu-liikennetapahtuma))))))

(defn nayta-suunnan-ketjutukset? [{:keys [valittu-liikennetapahtuma]} suunta tiedot]
  (boolean
    (and (some? tiedot)
         (or (= suunta (:valittu-suunta valittu-liikennetapahtuma))
             (= :molemmat (:valittu-suunta valittu-liikennetapahtuma))))))

(defn nayta-liikennegrid? [{:keys [valittu-liikennetapahtuma]}]
  (boolean
    (and (::lt/kohde valittu-liikennetapahtuma)
         (or (id-olemassa? (::lt/id valittu-liikennetapahtuma))
             (:valittu-suunta valittu-liikennetapahtuma)))))

(defn jarjesta-tapahtumat [tapahtumat]
  (sort-by
    ;; Tarvitaan aika monta vaihtoehtoista sorttausavainta, koska
    ;; yhdelle kohteelle voi tulla yhdellä kirjauksella aika monta riviä
    (juxt ::lt/aika
          (comp ::kohde/nimi ::lt/kohde)
          ::lt-alus/nimi
          ::lt-alus/laji
          ::lt-alus/lkm)
    (fn [[a-aika & _ :as a] [b-aika & _ :as b]]
      (if (time/equal? a-aika b-aika)
        (compare a b)
        (time/after? a-aika b-aika)))
    tapahtumat))

(defn nayta-lisatiedot? [app]
  (nayta-liikennegrid? app))

(defn kuittausta-odottavat [{:keys [siirretyt-alukset] :as app} alukset]
  (remove (comp (or siirretyt-alukset #{}) ::lt-alus/id) alukset))

(defn ketjutuksen-poisto-kaynnissa? [{:keys [ketjutuksen-poistot]} alus]
  (boolean (ketjutuksen-poistot (::lt-alus/id alus))))

(defn ketjutuksen-voi-siirtaa-tapahtumasta? [{:keys [siirretyt-alukset]} alus]
  (boolean (siirretyt-alukset (::lt-alus/id alus))))

(defn grid-virheita? [rivit]
  (boolean (some
             (comp not empty? :harja.ui.grid/virheet)
             (remove :poistettu (vals rivit)))))

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  HaeLiikennetapahtumat
  (process-event [_ app]
    (if-not (:liikennetapahtumien-haku-kaynnissa? app)
      (let [params (hakuparametrit app)]
        (-> app
            (tt/post! :hae-liikennetapahtumat
                      params
                      ;; Checkbox-group ja aluksen nimen kirjoitus generoisi
                      ;; liikaa requesteja ilman viivettä.
                      {:viive 1000
                       :tunniste :hae-liikennetapahtumat-liikenne-nakymaan
                       :lahetetty ->HaeLiikennetapahtumatKutsuLahetetty
                       :onnistui ->LiikennetapahtumatHaettu
                       :epaonnistui ->LiikennetapahtumatEiHaettu})
            (assoc :liikennetapahtumien-haku-tulee-olemaan-kaynnissa? true)))
      app))

  HaeLiikennetapahtumatKutsuLahetetty
  (process-event [_ app]
    (assoc app :liikennetapahtumien-haku-kaynnissa? true))

  LiikennetapahtumatHaettu
  (process-event [{tulos :tulos} app]
    (tapahtumat-haettu app tulos))

  LiikennetapahtumatEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Liikennetapahtumien haku epäonnistui! " :danger)
    (assoc app :liikennetapahtumien-haku-kaynnissa? false
           :liikennetapahtumien-haku-tulee-olemaan-kaynnissa? false))

  ValitseTapahtuma
  (process-event [{t :tapahtuma} app]
    (lt/paivita-suunnat-ja-toimenpide!)
    (swap! lt-alus/aluslajit* assoc :EI [lt-alus/lajittamaton-alus])
    (swap! lt/suunnat-atom assoc :ei-suuntaa "Ei määritelty")
    (-> app
        (assoc :valittu-liikennetapahtuma (when-let [tapahtuma (if (::lt/id t) (koko-tapahtuma t app) t)]
                                            (kohteenosatiedot-toimintoihin tapahtuma (::lt/kohde tapahtuma))))
        (assoc :siirretyt-alukset #{})
        (assoc :ketjutuksen-poistot #{})))

  HaeEdellisetTiedot
  (process-event [{t :tapahtuma} app]
    (let [params {::lt/urakka-id (get-in t [::lt/urakka ::ur/id])
                  ::lt/kohde-id (get-in t [::lt/kohde ::kohde/id])
                  ::lt/sopimus-id (get-in t [::lt/sopimus ::sop/id])}]
      (tt/post! :hae-edelliset-tapahtumat
                params
                {:onnistui ->EdellisetTiedotHaettu
                 :epaonnistui ->EdellisetTiedotEiHaettu})
      (assoc app :edellisten-haku-kaynnissa? true)))

  EdellisetTiedotHaettu
  (process-event [{t :tulos} app]
    (-> app
        (assoc-in [:edelliset :tama] (:edellinen t))
        (assoc-in [:valittu-liikennetapahtuma ::lt/vesipinta-alaraja] (get-in t [:edellinen ::lt/vesipinta-alaraja]))
        (assoc-in [:valittu-liikennetapahtuma ::lt/vesipinta-ylaraja] (get-in t [:edellinen ::lt/vesipinta-ylaraja]))
        (assoc-in [:edelliset :ylos] (:ylos t))
        (assoc-in [:edelliset :alas] (:alas t))
        (assoc-in [:edelliset :ei-suuntaa] (:ei-suuntaa t))
        (assoc :edellisten-haku-kaynnissa? false)))

  EdellisetTiedotEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Virhe edellisten tapahtumien haussa!" :danger)
    (-> app
        (assoc :edellisten-haku-kaynnissa? false)))

  PaivitaValinnat
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys u valintojen-avaimet))
          haku (tuck/send-async! ->HaeLiikennetapahtumat)]
      (go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  TapahtumaaMuokattu
  (process-event [{t :tapahtuma} app]
    (assoc app :valittu-liikennetapahtuma t))

  MuokkaaAluksia
  (process-event [{alukset :alukset v :virheita?} {tapahtuma :valittu-liikennetapahtuma :as app}]
      (if tapahtuma
        (-> app
            (assoc-in [:valittu-liikennetapahtuma ::lt/alukset] (kasittele-suunta-alukselle tapahtuma alukset))
            (assoc-in [:valittu-liikennetapahtuma :grid-virheita?] v))
        app))

  VaihdaSuuntaa
  (process-event [{alus :alus suunta :suunta} app]
    (let [uusi-suunta (if (:ei-suuntaa @lt/suunnat-atom)
                        (cond (= :ylos suunta) :alas
                              (= :alas suunta) :ei-suuntaa
                              (= :ei-suuntaa suunta) :ylos
                              :else :ylos)
                        (cond (= :ylos suunta) :alas
                              (= :alas suunta) :ylos
                              :else :ylos))

          uusi (assoc alus ::lt-alus/suunta uusi-suunta)]
      (update app :valittu-liikennetapahtuma
              (fn [t]
                (update t ::lt/alukset
                        (fn [alukset]
                          (map #(if (sama-alusrivi? uusi %) uusi %) alukset)))))))

  AsetaSuunnat
  (process-event [{suunta :suunta} app]
    (-> app
        (assoc-in [:valittu-liikennetapahtuma :valittu-suunta] suunta)
        (update-in [:valittu-liikennetapahtuma ::lt/alukset]
                   (fn [alukset]
                     (map #(assoc % ::lt-alus/suunta suunta) alukset)))))

  TallennaLiikennetapahtuma
  (process-event [{t :tapahtuma} {:keys [tallennus-kaynnissa?] :as app}]
    (if-not tallennus-kaynnissa?
      (let [params (-> (tallennusparametrit t)
                       (assoc :hakuparametrit (hakuparametrit app)))]
        (-> app
            (tt/post! :tallenna-liikennetapahtuma
                      params
                      {:onnistui ->TapahtumaTallennettu
                       :epaonnistui ->TapahtumaEiTallennettu})
            (assoc :tallennus-kaynnissa? true)))
      app))

  TapahtumaTallennettu
  (process-event [{t :tulos} app]
    ;; Lisää "ei aluslajia" ja "ei määritelty" tapahtumat näkymään, muuten sitä ei näy filttereissä
    (swap! lt-alus/aluslajit* assoc :EI [lt-alus/lajittamaton-alus])
    (swap! lt/suunnat-atom assoc :ei-suuntaa "Ei määritelty")
    (when (modal/nakyvissa?) (modal/piilota!))
    (-> app
        (assoc :tallennus-kaynnissa? false)
        (assoc :valittu-liikennetapahtuma nil)
        (tapahtumat-haettu t)))

  TapahtumaEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Virhe tapahtuman tallennuksessa!" :danger)
    (assoc app :tallennus-kaynnissa? false))

  SiirraKaikkiTapahtumaan
  (process-event [{alukset :alukset} app]
    (let [idt (map ::lt-alus/id alukset)]
      (-> app
          (update :siirretyt-alukset (fn [s] (if (nil? s) (set idt) (into s idt))))
          (update-in [:valittu-liikennetapahtuma ::lt/alukset] concat alukset))))

  SiirraTapahtumaan
  (process-event [{alus :alus} app]
    (-> app
        (update :siirretyt-alukset (fn [s] (if (nil? s) #{(::lt-alus/id alus)} (conj s (::lt-alus/id alus)))))
        (update-in [:valittu-liikennetapahtuma ::lt/alukset] conj alus)))

  SiirraTapahtumasta
  (process-event [{alus :alus} app]
    (-> app
        (update :siirretyt-alukset (fn [s] (disj s (::lt-alus/id alus))))
        (update-in [:valittu-liikennetapahtuma ::lt/alukset]
                   (fn [alukset]
                     (let [;; Peak human evolution
                           ;; Eli kun poistetaan rivi annetaan sille poistettu true, niin tämä merkataan poistetuksi myös kannasta kun tallennetaan
                           ;; Jos uusi rivi poistetaan, kantaan ei tehdä muutoksia 
                           uudet-alukset (into [] (merge (disj (into #{} alukset) alus) (-> alus
                                                                                            ;; Poistetun lisäksi virheiden ehkäisyn takia laji pois ja suunta ylös
                                                                                            (assoc :poistettu true)
                                                                                            (dissoc ::lt-alus/laji)
                                                                                            (assoc ::lt-alus/suunta :ylos))))]
                       ;; Palautetaan alukset sortattuna niin eivät mene sekaisin rivejä poistaessa 
                       (sort-by :id uudet-alukset))))))

  PoistaKetjutus
  (process-event [{a :alus} {:keys [ketjutuksen-poistot] :as app}]
    (let [id (::lt-alus/id a)]
      (if-not (ketjutuksen-poistot id)
        (-> app
            (tt/post! :poista-ketjutus
                      {::lt-alus/id id
                       ::lt/urakka-id (:id @nav/valittu-urakka)}
                      {:onnistui ->KetjutusPoistettu
                       :onnistui-parametrit [id]
                       :epaonnistui ->KetjutusEiPoistettu
                       :epaonnistui-parametrit [id]})
            (update :ketjutuksen-poistot (fn [s] (if (nil? s)
                                                   #{id}
                                                   (conj s id)))))

        app)))

  KetjutusPoistettu
  (process-event [{_ :tulos id :id} app]
    (when (modal/nakyvissa?) (modal/piilota!))
    (-> app
        (poista-ketjutus id)
        (update :ketjutuksen-poistot (fn [s] (if (nil? s)
                                               #{}
                                               (disj s id))))))

  KetjutusEiPoistettu
  (process-event [{_ :virhe id :id} app]
    (viesti/nayta! "Virhe ketjutuksen poistossa!" :danger)
    (-> app
        (update :ketjutuksen-poistot (fn [s] (if (nil? s)
                                               #{}
                                               (disj s id))))))
  AsetaAloitusTiedot
  (process-event [{:keys [aikavali]} app]
    (let [kanava-hallintayksikko (some #(when (= (:nimi %) "Kanavat ja avattavat sillat")
                                          (:id %))
                                       @hallintayksikot/vaylamuodon-hallintayksikot)]
      (-> app
          (tt/post! :kayttajan-urakat [kanava-hallintayksikko] {:onnistui ->KayttajanUrakatHaettu})
          (assoc-in [:valinnat :aikavali] aikavali))))

  KayttajanUrakatHaettu
  (process-event [{urakat :urakat} app]
    (let [urakat (when-not (empty? urakat)
                   (->> urakat
                        first
                        :urakat
                        (map #(if (= (:id %) @nav/valittu-urakka-id)
                                (-> %
                                    (assoc :valittu? true)
                                    (dissoc % :urakkanro))
                                (dissoc % :urakkanro)))))
          app (assoc-in app [:valinnat :kayttajan-urakat] urakat)]
      (tuck/process-event (->PaivitaValinnat (:valinnat app)) app)
      app))
  UrakkaValittu
  (process-event [{{:keys [id]} :urakka valittu? :valittu?} app]
    (let [uudet-urakkavalinnat (map #(if (= (:id %) id)
                                       (assoc % :valittu? valittu?)
                                       %)
                                    (get-in app [:valinnat :kayttajan-urakat]))]
      (tuck/process-event (->PaivitaValinnat {:kayttajan-urakat uudet-urakkavalinnat}) app)
      (assoc-in app [:valinnat :kayttajan-urakat] uudet-urakkavalinnat))))
