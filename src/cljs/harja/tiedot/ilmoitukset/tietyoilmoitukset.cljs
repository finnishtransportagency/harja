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
            [harja.domain.tietyoilmoitukset :as t]
            [harja.domain.tierekisteri :as tr]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.local-storage :refer [local-storage-atom]]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [harja.tiedot.istunto :as istunto]
            [harja.transit :as transit]
            [clojure.set :as set])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

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
   :tietyoilmoitukset nil
   :valinnat {:luotu-vakioaikavali (second luonti-aikavalit)
              :luotu-alkuaika (pvm/tuntia-sitten 24)
              :luotu-loppuaika (pvm/nyt)
              :kaynnissa-vakioaikavali (first kaynnissa-aikavalit)
              :kaynnissa-alkuaika (pvm/tunnin-paasta 24)
              :kaynnissa-loppuaika (pvm/tunnin-paasta 24)}})

(defn- muodosta-palautettu-tila [app]
  ;; kutsutaan, kun atomin sisältö ladataan LocalStoragesta
  (let [valittu-ilmoitus (:valittu-ilmoitus app)
        ok-namespacessa? #(if (-> % first namespace (= "harja.domain.tietyoilmoitukset"))
                            %)
        putsattu-ilmoitus (when valittu-ilmoitus
                            (into {} (keep ok-namespacessa? valittu-ilmoitus)))]
    (log "tti tvt: avainten lkmt" (count valittu-ilmoitus) (count putsattu-ilmoitus))

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

(defn- hae-yllapitokohteen-tiedot-tietyoilmoitukselle [yllapitokohde-id]
  (k/post! :hae-yllapitokohteen-tiedot-tietyoilmoitukselle yllapitokohde-id))

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
   ::t/osoite {::tr/geometria geometria
               ::tr/tie tr-numero
               ::tr/aosa tr-alkuosa
               ::tr/aet tr-alkuetaisyys
               ::tr/losa tr-loppuosa
               ::tr/let tr-loppuetaisyys}})

(defn esitayta-tietyoilmoitus [{:keys [yllapitokohde-id
                                       urakka-id
                                       urakka-nimi
                                       urakoitsija-nimi
                                       urakoitsijan-yhteyshenkilo
                                       tilaaja-nimi
                                       tilaajan-yhteyshenkilo
                                       kohteet]
                                :as data}]
  (let [kayttaja @istunto/kayttaja
        tietyoilmoitus {::t/urakka-id urakka-id
                        ::t/urakan-nimi urakka-nimi

                        ::t/urakoitsijan-nimi urakoitsija-nimi
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
(defrecord PaivitaNopeusrajoituksetGrid [nopeusrajoitukset])
(defrecord PaivitaTienPinnatGrid [tienpinnat avain])
(defrecord PaivitaTyoajatGrid [tyoajat])
(defrecord TallennaIlmoitus [ilmoitus sulje-ilmoitus avaa-pdf?])
(defrecord IlmoitusTallennettu [ilmoitus sulje-ilmoitus avaa-pdf?])
(defrecord IlmoitusEiTallennettu [virhe])
(defrecord AloitaUusiTietyoilmoitus [urakka-id])
(defrecord AloitaUusiTyovaiheilmoitus [tietyoilmoitus])
(defrecord UusiTietyoilmoitus [esitaytetyt-tiedot])
(defrecord UrakkaValittu [urakka-id])
(defrecord UrakanTiedotHaettu [urakka])
(defrecord ValitseYllapitokohde [yllapitokohde])
(defrecord YllapitokohdeValittu [yllapitokohde])

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
                                                  :urakka
                                                  :vain-kayttajan-luomat])]
            {:tietyoilmoitukset (async/<! (k/post! :hae-tietyoilmoitukset parametrit))}))))
    (assoc app :tietyoilmoitukset nil))

  IlmoituksetHaettu
  (process-event [vastaus app]
    (let [ilmoitukset (:tietyoilmoitukset (:tulokset vastaus))]
      (assoc app :tietyoilmoitukset ilmoitukset)))

  ValitseIlmoitus
  (process-event [{ilmoitus :ilmoitus} app]
    (assoc app :valittu-ilmoitus ilmoitus))

  PoistaIlmoitusValinta
  (process-event [_ app]
    (assoc app :valittu-ilmoitus nil))

  IlmoitustaMuokattu
  (process-event [ilmoitus app]
    #_(log "IlmoitustaMuokattu: saatiin" (keys ilmoitus) "ja" (keys app))

    (assoc app :valittu-ilmoitus (:ilmoitus ilmoitus)))

  HaeKayttajanUrakat
  (process-event [{hallintayksikot :hallintayksikot} app]
    (let [tulos! (tuck/send-async! ->KayttajanUrakatHaettu)]
      (go (tulos! (async/<! (k/post! :kayttajan-urakat (mapv :id hallintayksikot))))))
    (assoc app :kayttajan-urakat nil))

  KayttajanUrakatHaettu
  (process-event [{urakat :urakat} app]
    (let [urakat (sort-by :nimi (mapcat :urakat urakat))
          urakka (when @nav/valittu-urakka (:id @nav/valittu-urakka))]
      (assoc app :kayttajan-urakat urakat
                 :valinnat (assoc (:valinnat app) :urakka urakka))))

  PaivitaSijainti
  (process-event [{sijainti :sijainti} app]
    (assoc-in app [:valinnat :sijainti] sijainti))

  PaivitaIlmoituksenSijainti
  (process-event [{sijainti :sijainti} app]
    (assoc-in app [:valittu-ilmoitus ::t/osoite ::tr/geometria] sijainti))

  PaivitaNopeusrajoituksetGrid
  (process-event [{nopeusrajoitukset :nopeusrajoitukset} app]
    (assoc-in app [:valittu-ilmoitus ::t/nopeusrajoitukset] nopeusrajoitukset))

  PaivitaTienPinnatGrid
  (process-event [{:keys [tienpinnat avain] :as kamat} app]
    (assoc-in app [:valittu-ilmoitus avain] tienpinnat))

  PaivitaTyoajatGrid
  (process-event [{tyoajat :tyoajat} app]
    (assoc-in app [:valittu-ilmoitus ::t/tyoajat] tyoajat))

  TallennaIlmoitus
  (process-event [{ilmoitus :ilmoitus sulje-ilmoitus :sulje-ilmoitus avaa-pdf? :avaa-pdf?} app]
    (let [tulos! (tuck/send-async! ->IlmoitusTallennettu sulje-ilmoitus avaa-pdf?)
          fail! (tuck/send-async! ->IlmoitusEiTallennettu)]
      (go
        (try
          (let [vastaus-kanava (k/post! :tallenna-tietyoilmoitus
                                 (-> ilmoitus
                                     (dissoc ::t/tyovaiheet :urakan-kohteet)))
                vastaus (when vastaus-kanava
                          (<! vastaus-kanava))]
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (log "poikkeus lomakkeen tallennuksessa: " (pr-str e))
            (fail! nil)
            (throw e)))))
    (assoc app :tallennus-kaynnissa? true))

  IlmoitusTallennettu
  (process-event [{ilmoitus :ilmoitus sulje-ilmoitus :sulje-ilmoitus avaa-pdf? :avaa-pdf?} app]
    (viesti/nayta! "Ilmoitus tallennettu!")
    (log "avaa pdf tallennuksen jälkeen? " avaa-pdf?)
    (when avaa-pdf?
      (set! (.-location js/window) (k/pdf-url :tietyoilmoitus "parametrit" (transit/clj->transit {:id (::t/id ilmoitus)}))))
    (assoc app
      :tallennus-kaynnissa? false
      :valittu-ilmoitus (if sulje-ilmoitus nil ilmoitus)))

  IlmoitusEiTallennettu
  (process-event [{virhe :virhe} app]
    (viesti/nayta! [:span "Virhe tallennuksessa! Ilmoitusta ei tallennettu"]
                   :danger)
    (assoc app
      :tallennus-kaynnissa? false))

  AloitaUusiTietyoilmoitus
  (process-event [{urakka-id :urakka-id} app]
    (let [tulos! (tuck/send-async! ->UusiTietyoilmoitus)]
      (go
        (tulos! (esitayta-tietyoilmoitus (<! (hae-urakan-tiedot-tietyoilmoitukselle urakka-id))))))
    app)

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
    (assoc app :valittu-ilmoitus esitaytetyt-tiedot))

  UrakkaValittu
  (process-event [{urakka-id :urakka-id} app]
    (let [tulos! (tuck/send-async! ->UrakanTiedotHaettu)]
      (go
        (tulos! (<! (hae-urakan-tiedot-tietyoilmoitukselle urakka-id)))))
    app)

  UrakanTiedotHaettu
  (process-event [{urakka :urakka} app]
    (update app :valittu-ilmoitus merge (esitayta-tietyoilmoitus urakka)))

  ValitseYllapitokohde
  (process-event [{yllapitokohde :yllapitokohde} app]
    (let [tulos! (tuck/send-async! ->YllapitokohdeValittu)]
      (go
        (<! (async/timeout 1))
        (tulos! (yllapitokohteen-tiedot-tietyoilmoituksella yllapitokohde))))
    app)

  YllapitokohdeValittu
  (process-event [{yllapitokohde :yllapitokohde} app]
    (assoc app :valittu-ilmoitus (merge (:valittu-ilmoitus app) yllapitokohde))))

(defn avaa-tietyoilmoitus
  [tietyoilmoitus-id yllapitokohde]
  (go
    (let [tietyoilmoitus (if tietyoilmoitus-id
                           (<! (hae-tietyoilmoituksen-tiedot tietyoilmoitus-id))
                           (esitayta-tietyoilmoitus
                             (<! (hae-yllapitokohteen-tiedot-tietyoilmoitukselle (:id yllapitokohde)))))]
      (swap! tietyoilmoitukset #(assoc % :valittu-ilmoitus tietyoilmoitus
                                         :tallennus-kaynnissa? false)))))
