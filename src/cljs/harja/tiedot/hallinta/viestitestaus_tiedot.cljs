(ns harja.tiedot.hallinta.viestitestaus-tiedot
  "Sähköposti- ja tekstiviestitestauksen ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [cljs.core.async :refer [<! >! chan close!]]
            [cljs-http.client :as http]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))



(def alkutila {:email {:lahettaja "harja@testaa.fi"
                       :vastaanottaja "harja@vastaanottaa.com"
                       :palvelin "smtp.gmail.com"
                       :tunnus "harja@testaa.com"
                       :salasana "salakala"
                       :portti 587
                       :tls true
                       :otsikko "<anna otsikko>"
                       :viesti "<anna viesti>"
                       :lahetys-kaynnissa? false}
               :emailapi {:lahettaja "harja@vayla.fi"
                          :vastaanottaja "<vastaanottajansahkoposti>"
                          :otsikko "<anna otsikko>"
                          :viesti "<anna viesti>"
                          :lahetys-kaynnissa? false}
               :tekstiviesti {:puhelinnumero "<vastaanottajan puhelinnumero>"
                              :viesti "<anna viesti>"
                              :lahetys-kaynnissa? false}})
(def tila (atom alkutila))
(def nakymassa? (atom false))

;; Lokaalilla palvelimella tai Gmaililla lähetys
(defrecord Muokkaa [email])
(defrecord Laheta [email])
(defrecord LahetysOnnistui [vastaus])
(defrecord LahetysEpaonnistui [vastaus])

;; APIrajapinnalla lähetys
(defrecord MuokkaaAPI [emailapi])
(defrecord LahetaAPI [emailapi])
(defrecord LahetysAPIOnnistui [vastaus])
(defrecord LahetysAPIEpaonnistui [vastaus])

;; Tekstiviestilähetys
(defrecord MuokkaaSMS [tekstiviesti])
(defrecord LahetaSMS [tekstiviesti])
(defrecord LahetysSMSOnnistui [vastaus])
(defrecord LahetysSMSEpaonnistui [vastaus])

(extend-protocol tuck/Event

  Muokkaa
  (process-event [{emailapi :emailapi} app]
    (assoc app :emailapi emailapi))

  Laheta
  (process-event [{email :email} app]
    (let [data (dissoc email
                 :harja.ui.lomake/skeema
                 :harja.ui.lomake/puuttuvat-pakolliset-kentat
                 :harja.ui.lomake/viimeisin-muokkaus
                 :harja.ui.lomake/ensimmainen-muokkaus
                 :harja.ui.lomake/viimeksi-muokattu-kentta
                 :harja.ui.lomake/muokatut
                 :lahetys-kaynnissa?
                 :harja.ui.lomake/virheet
                 :harja.ui.lomake/varoitukset
                 :harja.ui.lomake/huomautukset)]
      (js/console.log "Lähetetään email: " (pr-str data))
      (tuck-apurit/post! :debug-laheta-email data
        {:onnistui ->LahetysOnnistui
         :epaonnistui ->LahetysEpaonnistui})
      (js/console.log "Lähetys matkalla!")
      (assoc app :lahetys-kaynnissa? true)))

  LahetysOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Email lähetetty" :onnistui)
      (js/console.log "Vastaus: " (pr-str vastaus))
      (assoc app :lahetys-kaynnissa? false)))

  LahetysEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Lähetys epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (js/console.error "Virhe: " (pr-str vastaus))
      (assoc app :lahetys-kaynnissa? false)))

  MuokkaaAPI
  (process-event [{emailapi :emailapi} app]
    (assoc app :emailapi emailapi))

  LahetaAPI
  (process-event [{emailapi :emailapi} app]
    (let [data (dissoc emailapi
                 :harja.ui.lomake/skeema
                 :harja.ui.lomake/puuttuvat-pakolliset-kentat
                 :harja.ui.lomake/viimeisin-muokkaus
                 :harja.ui.lomake/ensimmainen-muokkaus
                 :harja.ui.lomake/viimeksi-muokattu-kentta
                 :harja.ui.lomake/muokatut
                 :lahetys-kaynnissa?
                 :harja.ui.lomake/virheet
                 :harja.ui.lomake/varoitukset
                 :harja.ui.lomake/huomautukset)]
      (js/console.log "Lähetetään emailapi: " (pr-str data))
      (tuck-apurit/post! :debug-laheta-emailapi data
        {:onnistui ->LahetysAPIOnnistui
         :epaonnistui ->LahetysAPIEpaonnistui})
      (js/console.log "Lähetys matkalla!")
      (assoc app :lahetys-kaynnissa? true)))

  LahetysAPIOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Emailapi lähetetty" :onnistui)
      (js/console.log "Vastaus: " (pr-str vastaus))
      (assoc app :lahetys-kaynnissa? false)))

  LahetysAPIEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Lähetys epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (js/console.error "Virhe: " (pr-str vastaus))
      (assoc app :lahetys-kaynnissa? false)))

  MuokkaaSMS
  (process-event [{tekstiviesti :tekstiviesti} app]
    (assoc app :tekstiviesti tekstiviesti))

  LahetaSMS
  (process-event [{tekstiviesti :tekstiviesti} app]
    (let [data (dissoc tekstiviesti
                 :harja.ui.lomake/skeema
                 :harja.ui.lomake/puuttuvat-pakolliset-kentat
                 :harja.ui.lomake/viimeisin-muokkaus
                 :harja.ui.lomake/ensimmainen-muokkaus
                 :harja.ui.lomake/viimeksi-muokattu-kentta
                 :harja.ui.lomake/muokatut
                 :lahetys-kaynnissa?
                 :harja.ui.lomake/virheet
                 :harja.ui.lomake/varoitukset
                 :harja.ui.lomake/huomautukset)]
      (js/console.log "Lähetetään tekstiviesti: " (pr-str data))
      (tuck-apurit/post! :debug-laheta-tekstiviesti data
        {:onnistui ->LahetysSMSOnnistui
         :epaonnistui ->LahetysSMSEpaonnistui})
      (js/console.log "Lähetys matkalla!")
      (assoc app :lahetys-kaynnissa? true)))

  LahetysSMSOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Tekstiviesti lähetetty" :onnistui)
      (js/console.log "Vastaus: " (pr-str vastaus))
      (assoc app :lahetys-kaynnissa? false)))

  LahetysSMSEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Lähetys epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (js/console.error "Virhe: " (pr-str vastaus))
      (assoc app :lahetys-kaynnissa? false))))
