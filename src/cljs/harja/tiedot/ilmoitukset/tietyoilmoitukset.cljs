(ns harja.tiedot.ilmoitukset.tietyoilmoitukset
  (:require [reagent.core :refer [atom]]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakat :as tiedot-urakat]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :as async]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [tuck.core :as tuck]
            [harja.domain.tietyoilmoitus :as t]
            [harja.domain.tietyoilmoituksen-email :as e]
            [harja.domain.kayttaja :as ka]
            [harja.domain.tierekisteri :as tr]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.local-storage :refer [local-storage-atom]]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [harja.tiedot.istunto :as istunto]
            [harja.transit :as transit]
            [clojure.set :as set])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def tieliikennekeskusten-sahkopostiosoitteet
  (if (k/kehitysymparistossa?)
    [["Helsinki" "helsinki.liikennekeskus_TESTI@example.org"]
     ["Oulu" "oulu.liikennekeskus_TESTI@example.org"]
     ["Tampere" "tampere.liikennekeskus_TESTI@example.org"]
     ["Turku" "turku.liikennekeskus_TESTI@example.org"]]

    ;; Tuotantoversion osoitteet
    [["Helsinki" "helsinki.liikennekeskus@vayla.fi"]
     ["Oulu" "oulu.liikennekeskus@vayla.fi"]
     ["Tampere" "tampere.liikennekeskus@vayla.fi"]
     ["Turku" "turku.liikennekeskus@vayla.fi"]]))

(def luonti-aikavalit [{:nimi "Ei rajausta" :ei-rajausta? true}
                       {:nimi "1 päivän ajalta" :tunteja 24}
                       {:nimi "1 viikon ajalta" :tunteja 168}
                       {:nimi "4 viikon ajalta" :tunteja 672}
                       {:nimi "Vapaa aikaväli" :vapaa-aikavali true}])

(def kaynnissa-aikavalit [{:nimi "Ei rajausta" :ei-rajausta? true}
                          {:nimi "1 päivän sisällä" :tunteja 24}
                          {:nimi "1 viikon sisällä" :tunteja 168}
                          {:nimi "4 viikon sisällä" :tunteja 672}
                          {:nimi "Vapaa aikaväli" :vapaa-aikavali true}])

(defonce ulkoisetvalinnat
         (reaction {:voi-hakea? true
                    :hallintayksikko (:id @nav/valittu-hallintayksikko)
                    :urakka @nav/valittu-urakka
                    :valitun-urakan-hoitokaudet @tiedot-urakka/valitun-urakan-hoitokaudet
                    :urakoitsija (:id @nav/valittu-urakoitsija)
                    :urakkatyyppi (:arvo @nav/urakkatyyppi)
                    :hoitokausi @tiedot-urakka/valittu-hoitokausi}))

(def tietyoilmoitus-app-sapluuna
  {:ilmoitusnakymassa? false
   :valittu-ilmoitus nil
   :tallennus-kaynnissa? false
   :haku-kaynnissa? false
   :aloitetaan-uusi-tietyoilmoitus? false
   :tietyoilmoitukset nil
   :sahkopostilahetyksen-modal-data {:nakyvissa? false
                                     :avaa-pdf? false
                                     :ilmoitus nil
                                     :lomakedata {:vastaanottaja nil
                                                  :muut-vastaanottajat #{}
                                                  :kopio-itselle? false}}
   :valinnat {:luotu-vakioaikavali (second luonti-aikavalit)
              :luotu-alkuaika (pvm/tuntia-sitten 24)
              :luotu-loppuaika (pvm/nyt)
              :kaynnissa-vakioaikavali (first kaynnissa-aikavalit)
              :kaynnissa-alkuaika (pvm/tunnin-paasta 24)
              :kaynnissa-loppuaika (pvm/tunnin-paasta 24)}
   :pollattavat-ilmoitukset #{}})

(defn- muodosta-palautettu-tila [app]
  ;; kutsutaan, kun atomin sisältö ladataan LocalStoragesta
  (let [valittu-ilmoitus (:valittu-ilmoitus app)
        ok-namespacessa? #(if (-> % first namespace #{"harja.domain.tietyoilmoitus" "harja.domain.muokkaustiedot"})
                            %)
        putsattu-ilmoitus (when valittu-ilmoitus
                            (into {} (keep ok-namespacessa? valittu-ilmoitus)))]

    (merge tietyoilmoitus-app-sapluuna
           {:valittu-ilmoitus putsattu-ilmoitus
            :kayttajan-urakat (:kayttajan-urakat app)})))

(defonce tietyoilmoitukset
         (local-storage-atom
           :tietyoilmoitukset
           tietyoilmoitus-app-sapluuna
           muodosta-palautettu-tila))


(defonce karttataso-tietyoilmoitukset (atom false))

(defonce tietyoilmoitukset-kartalla
         (reaction
           (let [{:keys [tietyoilmoitukset valittu-ilmoitus]} @tietyoilmoitukset]
             (when @karttataso-tietyoilmoitukset
               (kartalla-esitettavaan-muotoon
                 (map #(assoc % :tyyppi-kartalla :tietyoilmoitus) tietyoilmoitukset)
                 #(= (::t/id %) (::t/id valittu-ilmoitus)))))))

(defonce karttataso-ilmoitukset (atom false))

(defn- hae-tietyoilmoituksen-tiedot [tietyoilmoitus-id]
  (k/post! :hae-tietyoilmoitus tietyoilmoitus-id))

(defn- hae-yllapitokohteen-tiedot-tietyoilmoitukselle [{:keys [yllapitokohde-id valittu-urakka-id]}]
  (k/post! :hae-yllapitokohteen-tiedot-tietyoilmoitukselle {:yllapitokohde-id yllapitokohde-id
                                                            :valittu-urakka-id valittu-urakka-id}))

(defn- hae-urakan-tiedot-tietyoilmoitukselle [urakka-id]
  (k/post! :hae-urakan-tiedot-tietyoilmoitukselle urakka-id))

(defn yllapitokohteen-tiedot-tietyoilmoituksella [{:keys [yllapitokohde-id
                                                          alku
                                                          loppu
                                                          tr-numero
                                                          tr-alkuosa
                                                          tr-alkuetaisyys
                                                          tr-loppuosa
                                                          tr-loppuetaisyys
                                                          geometria]
                                                   :as data}]
  {::t/yllapitokohde yllapitokohde-id
   ::t/alku alku
   ::t/loppu loppu
   ::t/kohteen-aikataulu {:kohteen-alku (:alku data)
                          :paallystys-valmis (:loppu data)}
   ::t/osoite {::tr/geometria geometria
               ::tr/tie tr-numero
               ::tr/aosa tr-alkuosa
               ::tr/aet tr-alkuetaisyys
               ::tr/losa tr-loppuosa
               ::tr/let tr-loppuetaisyys}})

(defn esitayta-tietyoilmoitus [{:keys [yllapitokohde-id
                                       urakka-id
                                       urakka-nimi
                                       urakkatyyppi
                                       urakoitsija-nimi
                                       urakoitsija-ytunnus
                                       urakoitsijan-yhteyshenkilo
                                       tilaaja-nimi
                                       tilaajan-yhteyshenkilo
                                       kohteet]
                                :as data}]
  (let [kayttaja @istunto/kayttaja
        tietyoilmoitus {::t/urakka-id urakka-id
                        ::t/urakan-nimi urakka-nimi
                        ::t/urakkatyyppi urakkatyyppi
                        ::t/urakoitsijan-nimi urakoitsija-nimi
                        ::t/urakoitsijan-ytunnus urakoitsija-ytunnus
                        ::t/urakoitsijayhteyshenkilo {::t/etunimi (:etunimi urakoitsijan-yhteyshenkilo)
                                                      ::t/sukunimi (:sukunimi urakoitsijan-yhteyshenkilo)
                                                      ::t/matkapuhelin (:puhelin urakoitsijan-yhteyshenkilo)}
                        ::t/tilaajan-nimi tilaaja-nimi
                        ::t/tilaajayhteyshenkilo {::t/etunimi (:etunimi tilaajan-yhteyshenkilo)
                                                  ::t/sukunimi (:sukunimi tilaajan-yhteyshenkilo)
                                                  ::t/matkapuhelin (:puhelin urakoitsijan-yhteyshenkilo)}
                        ::t/ilmoittaja {::t/etunimi (:etunimi kayttaja)
                                        ::t/sukunimi (:sukunimi kayttaja)
                                        ::t/sahkoposti (:sahkoposti kayttaja)
                                        ::t/matkapuhelin (:puhelin kayttaja)}
                        :urakan-kohteet kohteet}]
    (if yllapitokohde-id
      (merge tietyoilmoitus (yllapitokohteen-tiedot-tietyoilmoituksella data))
      tietyoilmoitus)))

(defn pollataan-uudestaan?
  [lahetysten-tiedot entiset-email-lahetys-idt uusi-lahetetty?]
  (let [kesken-olevat-lahetykset (filter #(and (::e/lahetetty %)
                                               (not (::e/kuitattu %))
                                               (not (::e/lahetysvirhe %)))
                                         lahetysten-tiedot)
        joku-lahetys-kesken? (not (empty? kesken-olevat-lahetykset))
        uusi-sahkoposti-lahetetty? (when uusi-lahetetty?
                                     (not (empty? (remove #(entiset-email-lahetys-idt (::e/id %))
                                                          lahetysten-tiedot))))]
    (or joku-lahetys-kesken?
        (if (nil? uusi-sahkoposti-lahetetty?)
          false
          (not uusi-sahkoposti-lahetetty?)))))


(defn pollaa-sahkopostin-lahetysta [muuta-sahkopostin-tila-fn parametrit entiset-lahetykset uusi-lahetetty?]
  (go-loop [lahetysten-tiedot (async/<! (k/post! :hae-ilmoituksen-sahkopostitiedot parametrit))
            pollataan? (pollataan-uudestaan? lahetysten-tiedot entiset-lahetykset uusi-lahetetty?)
            aika 1000]
           (if pollataan?
             (do
               (async/<! (async/timeout (min aika 10000)))
               (recur (async/<! (k/post! :hae-ilmoituksen-sahkopostitiedot parametrit))
                      (pollataan-uudestaan? lahetysten-tiedot entiset-lahetykset uusi-lahetetty?)
                      (* 2 aika)))
             (muuta-sahkopostin-tila-fn lahetysten-tiedot (select-keys parametrit [::t/id])))))

(defrecord AsetaValinnat [valinnat])
(defrecord YhdistaValinnat [ulkoisetvalinnat])
(defrecord HaeIlmoitukset [])
(defrecord IlmoituksetHaettu [tulokset])
(defrecord ValitseIlmoitus [ilmoitus])
(defrecord PoistaIlmoitusValinta [])
(defrecord IlmoitustaMuokattu [ilmoitus])
(defrecord HaeKayttajanUrakat [hallintayksikot])
(defrecord KayttajanUrakatHaettu [urakat])
(defrecord PaivitaSijainti [sijainti])
(defrecord PaivitaIlmoituksenSijainti [sijainti])
(defrecord PaivitaNopeusrajoitukset [nopeusrajoitukset])
(defrecord PaivitaTienPinnat [tienpinnat avain])
(defrecord PaivitaTyoajat [tyoajat virheita?])
(defrecord PaivitaSahkopostilahetyksenTila [sahkopostien-tiedot ilmoituksen-tiedot])
(defrecord TallennaIlmoitus [ilmoitus sulje-ilmoitus avaa-pdf? sahkopostitiedot])
(defrecord IlmoitusTallennettu [ilmoitus sulje-ilmoitus avaa-pdf? laheta-sahkoposti?])
(defrecord IlmoitusEiTallennettu [virhe])
(defrecord AvaaSahkopostinLahetysModal [ilmoitus avaa-pdf?])
(defrecord SuljeSahkopostinLahetysModal [])
(defrecord SahkopostiModalinLomakettaMuokattu [lomakedata])
(defrecord SahkopostinMuitaVastaanottajiaMuokattu [muut-vastaanottajat])
(defrecord AloitaUusiTietyoilmoitus [urakka-id])
(defrecord AloitaUusiTyovaiheilmoitus [tietyoilmoitus])
(defrecord UusiTietyoilmoitus [esitaytetyt-tiedot])
(defrecord UrakkaValittu [urakka-id])
(defrecord UrakanTiedotHaettu [urakka])
(defrecord ValitseYllapitokohde [yllapitokohde])
(defrecord YllapitokohdeValittu [yllapitokohde])

(defn aloita-ilmoitusten-pollaaminen [ilmoitukset jo-pollattavat-ilmoitukset]
  (into #{}
        (for [ilmoitus ilmoitukset
              :let [email-lahetykset (::t/email-lahetykset ilmoitus)]
              :when (and
                      (not (jo-pollattavat-ilmoitukset (::t/id ilmoitus)))
                      (some #(and (::e/lahetetty %)
                                  (not (::e/kuitattu %))
                                  (not (::e/lahetysvirhe %)))
                            email-lahetykset))]
          (do (tuck/action!
                (fn [e!]
                  (pollaa-sahkopostin-lahetysta (fn [sahkopostien-tiedot ilmoituksen-tiedot]
                                                  (e! (->PaivitaSahkopostilahetyksenTila sahkopostien-tiedot ilmoituksen-tiedot)))
                                                (select-keys ilmoitus [::t/id ::t/urakka-id])
                                                (into #{}
                                                      (map ::e/id email-lahetykset))
                                                false)))
              (::t/id ilmoitus)))))


(defn- hae-ilmoitukset [{valinnat :valinnat haku :ilmoitushaku-id :as app}]
  (when haku
    (.clearTimeout js/window haku))
  (assoc app :ilmoitushaku-id (.setTimeout js/window (tuck/send-async! ->HaeIlmoitukset) 1000)))

(extend-protocol tuck/Event
  AsetaValinnat
  (process-event [{valinnat :valinnat} app]
    (hae-ilmoitukset (assoc app :valinnat valinnat)))

  YhdistaValinnat
  (process-event [{ulkoisetvalinnat :ulkoisetvalinnat :as e} app]
    (let [uudet-valinnat (merge ulkoisetvalinnat (:valinnat app))
          app (assoc app :valinnat uudet-valinnat)]
      (hae-ilmoitukset app)))

  HaeIlmoitukset
  (process-event [_ {valinnat :valinnat :as app}]
    (let [tulos! (tuck/send-async! ->IlmoituksetHaettu)]
      (go
        (tulos!
          (let [parametrit (select-keys valinnat [:luotu-alkuaika
                                                  :luotu-loppuaika
                                                  :luotu-vakioaikavali
                                                  :kaynnissa-alkuaika
                                                  :kaynnissa-loppuaika
                                                  :kaynnissa-vakioaikavali
                                                  :sijainti
                                                  :urakka-id
                                                  :vain-kayttajan-luomat])]
            {:tietyoilmoitukset (async/<! (k/post! :hae-tietyoilmoitukset parametrit))}))))
    (assoc app :tietyoilmoitukset nil))

  IlmoituksetHaettu
  (process-event [vastaus app]
    (let [ilmoitukset (:tietyoilmoitukset (:tulokset vastaus))
          pollattavat-ilmoitukset (when (and (istunto/ominaisuus-kaytossa? :tietyoilmoitusten-lahetys)
                                             (not (:pollaus-kaynnissa? app)))
                                    (aloita-ilmoitusten-pollaaminen ilmoitukset (:pollattavat-ilmoitukset app)))]

      (-> app
          (assoc :tietyoilmoitukset ilmoitukset)
          (update :pollattavat-ilmoitukset (fn [ilmoitus-idt]
                                             (set/union ilmoitus-idt pollattavat-ilmoitukset))))))

  ValitseIlmoitus
  (process-event [{ilmoitus :ilmoitus} app]
    (assoc app :valittu-ilmoitus ilmoitus))

  PoistaIlmoitusValinta
  (process-event [_ app]
    (assoc app :valittu-ilmoitus nil))

  IlmoitustaMuokattu
  (process-event [ilmoitus app]
    (assoc app :valittu-ilmoitus (:ilmoitus ilmoitus)))

  HaeKayttajanUrakat
  (process-event [{hallintayksikot :hallintayksikot} app]
    (let [tulos! (tuck/send-async! ->KayttajanUrakatHaettu)]
      (go (tulos! (async/<! (k/post! :kayttajan-urakat (mapv :id hallintayksikot))))))
    (assoc app :kayttajan-urakat nil))

  KayttajanUrakatHaettu
  (process-event [{urakat :urakat} app]
    (let [urakat (sort-by :nimi (mapcat :urakat urakat))
          urakka @nav/valittu-urakka-id]
      (assoc app :kayttajan-urakat urakat
             :valinnat (assoc (:valinnat app) :urakka-id urakka))))

  PaivitaSijainti
  (process-event [{sijainti :sijainti} app]
    (assoc-in app [:valinnat :sijainti] sijainti))

  PaivitaIlmoituksenSijainti
  (process-event [{sijainti :sijainti} app]
    (assoc-in app [:valittu-ilmoitus ::t/osoite ::tr/geometria] sijainti))

  PaivitaNopeusrajoitukset
  (process-event [{nopeusrajoitukset :nopeusrajoitukset} app]
    (assoc-in app [:valittu-ilmoitus ::t/nopeusrajoitukset] nopeusrajoitukset))

  PaivitaTienPinnat
  (process-event [{:keys [tienpinnat avain] :as kamat} app]
    (assoc-in app [:valittu-ilmoitus avain] tienpinnat))

  PaivitaTyoajat
  (process-event [{tyoajat :tyoajat virheita? :virheita?} app]
    (-> app
        (assoc-in [:valittu-ilmoitus ::t/tyoajat] tyoajat)
        (assoc-in [:valittu-ilmoitus :komponentissa-virheita? :tyoajat] virheita?)))

  PaivitaSahkopostilahetyksenTila
  (process-event [{sahkopostien-tiedot :sahkopostien-tiedot
                   {ilmoitus-id ::t/id} :ilmoituksen-tiedot} app]
    (-> app
        (update :tietyoilmoitukset (fn [ilmoitukset]
                                     (mapv (fn [ilmoitus]
                                             (if (= ilmoitus-id (::t/id ilmoitus))
                                               (assoc ilmoitus ::t/email-lahetykset sahkopostien-tiedot)
                                               ilmoitus))
                                           ilmoitukset)))
        (update :pollattavat-ilmoitukset (fn [ilmoitus-idt]
                                           (disj ilmoitus-idt ilmoitus-id)))))

  TallennaIlmoitus
  (process-event [{ilmoitus :ilmoitus sulje-ilmoitus :sulje-ilmoitus
                   avaa-pdf? :avaa-pdf? sahkopostitiedot :sahkopostitiedot} app]
    (let [tulos! (tuck/send-async! ->IlmoitusTallennettu sulje-ilmoitus avaa-pdf? (not (empty? sahkopostitiedot)))
          fail! (tuck/send-async! ->IlmoitusEiTallennettu)]
      (go
        (try
          (let [ilmoitus (-> ilmoitus
                             (dissoc ::t/tyovaiheet
                                     ::t/kohteen-aikataulu
                                     ::t/email-lahetykset
                                     :urakan-kohteet
                                     :komponentissa-virheita?
                                     :aihe
                                     :type
                                     :alue)
                             (update ::t/tyoajat
                                     (partial remove :poistettu)))
                vastaus-kanava (k/post! :tallenna-tietyoilmoitus {:ilmoitus ilmoitus
                                                                  :sahkopostitiedot sahkopostitiedot})
                vastaus (when vastaus-kanava
                          (async/<! vastaus-kanava))]
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (log "poikkeus lomakkeen tallennuksessa: " (pr-str e))
            (fail! nil)
            (throw e)))))
    (assoc app :tallennus-kaynnissa? true
           ;; suljetaan ja resetoidaan tallennuksen jälkeen modaali
           :sahkopostilahetyksen-modal-data {:nakyvissa? false
                                             :avaa-pdf? false
                                             :ilmoitus nil
                                             :lomakedata {:vastaanottaja nil
                                                          :muut-vastaanottajat #{}
                                                          :kopio-itselle? false}}))

  IlmoitusTallennettu
  (process-event [{ilmoitus :ilmoitus sulje-ilmoitus :sulje-ilmoitus avaa-pdf? :avaa-pdf? laheta-sahkoposti? :laheta-sahkoposti?} app]
    (if laheta-sahkoposti?
      (viesti/nayta! "Ilmoitus tallennettu, mutta sähköpostin lähetys on vielä kesken..." :info viesti/viestin-nayttoaika-keskipitka)
      (viesti/nayta! "Ilmoitus tallennettu!"))
    (when (and laheta-sahkoposti?
               (not ((:pollattavat-ilmoitukset app) (::t/id ilmoitus))))
      (tuck/action!
        (fn [e!]
          (pollaa-sahkopostin-lahetysta (fn [sahkopostien-tiedot ilmoituksen-tiedot]
                                          (e! (->PaivitaSahkopostilahetyksenTila sahkopostien-tiedot ilmoituksen-tiedot)))
                                        (select-keys ilmoitus [::t/id ::t/urakka-id])
                                        (into #{}
                                              (map ::e/id (::t/email-lahetykset ilmoitus)))
                                        true))))
    (when avaa-pdf?
      (set! (.-location js/window) (k/pdf-url :tietyoilmoitus "parametrit" (transit/clj->transit {:id (::t/id ilmoitus)}))))
    (-> app
        (assoc :tallennus-kaynnissa? false
               :valittu-ilmoitus (if sulje-ilmoitus nil ilmoitus))
        (update :pollattavat-ilmoitukset (fn [ilmoitus-idt]
                                           (if (and laheta-sahkoposti?
                                                    (not ((:pollattavat-ilmoitukset app) (::t/id ilmoitus))))
                                             (conj ilmoitus-idt (::t/id ilmoitus))
                                             ilmoitus-idt)))))

  IlmoitusEiTallennettu
  (process-event [{virhe :virhe} app]
    (viesti/nayta! [:span "Virhe tallennuksessa! Ilmoitusta ei tallennettu"]
                   :danger)
    (assoc app
           :tallennus-kaynnissa? false))

  AvaaSahkopostinLahetysModal
  (process-event [{ilmoitus :ilmoitus
                   avaa-pdf? :avaa-pdf?} app]
    (assoc app
           :sahkopostilahetyksen-modal-data {:nakyvissa? true
                                             :avaa-pdf? avaa-pdf?
                                             :ilmoitus ilmoitus
                                             :lomakedata {}}))

  SuljeSahkopostinLahetysModal
  (process-event [_ app]
    (assoc-in app
              [:sahkopostilahetyksen-modal-data :nakyvissa?] false))

  SahkopostiModalinLomakettaMuokattu
  (process-event [{lomakedata :lomakedata} app]
    (log "SahkopostiModalinLomakettaMuokattu, lomakedata: " (pr-str lomakedata))
    (assoc-in app
              [:sahkopostilahetyksen-modal-data :lomakedata] lomakedata))

  SahkopostinMuitaVastaanottajiaMuokattu
  (process-event [{muut-vastaanottajat :muut-vastaanottajat} app]
    (log "SahkopostinMuitaVastaanottajiaMuokattu, muut-vastaanottajat: " (pr-str muut-vastaanottajat))
    (assoc-in app
              [:sahkopostilahetyksen-modal-data :lomakedata :muut-vastaanottajat] muut-vastaanottajat))

  AloitaUusiTietyoilmoitus
  (process-event [{urakka-id :urakka-id} app]
    (let [tulos! (tuck/send-async! ->UusiTietyoilmoitus)]
      (go
        (tulos! (esitayta-tietyoilmoitus (async/<! (hae-urakan-tiedot-tietyoilmoitukselle urakka-id))))))
    (assoc app :aloitetaan-uusi-tietyoilmoitus? true))

  AloitaUusiTyovaiheilmoitus
  (process-event [{tietyoilmoitus :tietyoilmoitus} app]
    (let [tulos! (tuck/send-async! ->UusiTietyoilmoitus)]
      (go
        (tulos! (-> tietyoilmoitus
                    (assoc ::t/paatietyoilmoitus (::t/id tietyoilmoitus))
                    (dissoc ::t/id)))))
    app)

  UusiTietyoilmoitus
  (process-event [{esitaytetyt-tiedot :esitaytetyt-tiedot} app]
    (assoc app :valittu-ilmoitus esitaytetyt-tiedot
           :aloitetaan-uusi-tietyoilmoitus? false))

  UrakkaValittu
  (process-event [{urakka-id :urakka-id} app]
    (let [tulos! (tuck/send-async! ->UrakanTiedotHaettu)]
      (go
        (tulos! (async/<! (hae-urakan-tiedot-tietyoilmoitukselle urakka-id)))))
    app)

  UrakanTiedotHaettu
  (process-event [{urakka :urakka} app]
    (update app :valittu-ilmoitus merge (esitayta-tietyoilmoitus urakka)))

  ValitseYllapitokohde
  (process-event [{yllapitokohde :yllapitokohde} app]
    (let [tulos! (tuck/send-async! ->YllapitokohdeValittu)]
      (go
        (async/<! (async/timeout 1))
        (tulos! (yllapitokohteen-tiedot-tietyoilmoituksella yllapitokohde))))
    app)

  YllapitokohdeValittu
  (process-event [{yllapitokohde :yllapitokohde} app]
    (assoc app :valittu-ilmoitus (merge (:valittu-ilmoitus app) yllapitokohde))))

(defn avaa-tietyoilmoitus
  [tietyoilmoitus-id yllapitokohde valittu-urakka-id]
  (go
    (let [tietyoilmoitus (if tietyoilmoitus-id
                           (async/<! (hae-tietyoilmoituksen-tiedot tietyoilmoitus-id))
                           (esitayta-tietyoilmoitus
                             (async/<! (hae-yllapitokohteen-tiedot-tietyoilmoitukselle {:yllapitokohde-id (:id yllapitokohde)
                                                                                        :valittu-urakka-id valittu-urakka-id}))))]
      (swap! tietyoilmoitukset #(assoc % :valittu-ilmoitus tietyoilmoitus
                                         :tallennus-kaynnissa? false)))))
