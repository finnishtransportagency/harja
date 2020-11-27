(ns harja.tiedot.urakka.pot2.pot2-tiedot

  (:require
    [reagent.core :refer [atom] :as r]
    [tuck.core :refer [process-event] :as tuck]
    [harja.domain.pot2 :as pot2-domain]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.loki :refer [log tarkkaile!]]
    [harja.ui.lomakkeen-muokkaus :as lomakkeen-muokkaus]
    [harja.tiedot.urakka.paallystys :as paallystys]
    [harja.domain.oikeudet :as oikeudet])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(defonce pot2-nakymassa? (atom false))

(defrecord MuutaTila [polku arvo])
(defrecord PaivitaTila [polku f])
(defrecord HaePot2Tiedot [paallystyskohde-id])
(defrecord HaePot2TiedotOnnistui [vastaus])
(defrecord HaePot2TiedotEpaonnistui [vastaus])
(defrecord TallennaPot2Tiedot [])

(extend-protocol tuck/Event

  MuutaTila
  (process-event [{:keys [polku arvo]} app]
    (assoc-in app polku arvo))
  PaivitaTila
  (process-event [{:keys [polku f]} app]
    (update-in app polku f))

  HaePot2Tiedot
  (process-event [{paallystyskohde-id :paallystyskohde-id} {urakka :urakka :as app}]
    (let [parametrit {:urakka-id (:id urakka)
                      :paallystyskohde-id paallystyskohde-id}]
      (tuck-apurit/post! app
                         :urakan-paallystysilmoitus-paallystyskohteella
                         parametrit
                         {:onnistui ->HaePot2TiedotOnnistui
                          :epaonnistui ->HaePot2TiedotEpaonnistui})))

  HaePot2TiedotOnnistui
  (process-event [{vastaus :vastaus} {urakka :urakka :as app}]
    (let [perustiedot (select-keys vastaus paallystys/perustiedot-avaimet)
          kohdeosat (get-in vastaus [:ilmoitustiedot :osoitteet])
          lomakedata {:paallystyskohde-id (:paallystyskohde-id vastaus)
                      :perustiedot (merge perustiedot
                                          {:tr-osoite (select-keys perustiedot paallystys/tr-osoite-avaimet)})
                      :kirjoitusoikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                                                  (:id urakka))
                      :kohdeosat kohdeosat}]
      (-> app
          (assoc :paallystysilmoitus-lomakedata lomakedata))))

  HaePot2TiedotEpaonnistui
  ;; fixme implement
  (process-event [{vastaus :vastaus} app]
    (println "HaePot2TiedotEpaonnistui " (pr-str vastaus))
    app)

  TallennaPot2Tiedot
  (process-event [_ {{urakka-id :id :as urakka} :urakka
                     {:keys [valittu-sopimusnumero valittu-urakan-vuosi]} :urakka-tila
                     paallystysilmoitus-lomakedata :paallystysilmoitus-lomakedata :as app}]
    (println "Jarno TallennaPot2Tiedot paallystysilmoitus-lomakedata: " (pr-str paallystysilmoitus-lomakedata))
    (let [lahetettava-data (-> paallystysilmoitus-lomakedata
                               ;; Otetaan vain backin tarvitsema data
                               (select-keys #{:perustiedot :ilmoitustiedot :paallystyskohde-id})
                               (update :ilmoitustiedot dissoc :virheet)
                               (update :perustiedot lomakkeen-muokkaus/ilman-lomaketietoja)
                               (update-in [:perustiedot :asiatarkastus] lomakkeen-muokkaus/ilman-lomaketietoja)
                               (update-in [:perustiedot :tekninen-osa] lomakkeen-muokkaus/ilman-lomaketietoja)

                           )]
      (log "[PÄÄLLYSTYS] Lomake-data: " (pr-str paallystysilmoitus-lomakedata))
      (log "[PÄÄLLYSTYS] Lähetetään data " (pr-str lahetettava-data))
      (tuck-apurit/post! app :tallenna-paallystysilmoitus
                         {:urakka-id urakka-id
                          :sopimus-id (first valittu-sopimusnumero)
                          :vuosi valittu-urakan-vuosi
                          :paallystysilmoitus lahetettava-data}
                         {:onnistui paallystys/->TallennaPaallystysilmoitusOnnistui
                          :epaonnistui paallystys/->TallennaPaallystysilmoitusEpaonnistui
                          :paasta-virhe-lapi? true})))

  )