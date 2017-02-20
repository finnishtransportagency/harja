(ns harja-laadunseuranta.main
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.ui.kamera :as kamera]
            [harja-laadunseuranta.ui.kartta :as kartta]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.tiedot.kamera :as kamera-tiedot]
            [harja-laadunseuranta.tiedot.ilmoitukset :as ilmoitukset-tiedot]
            [harja-laadunseuranta.ui.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.ui.alustus :as alustus]
            [harja-laadunseuranta.ui.ylapalkki :as ylapalkki]
            [harja-laadunseuranta.ui.paanavigointi :refer [paanavigointi]]
            [harja-laadunseuranta.tiedot.tr-haku :as tr-haku]
            [harja-laadunseuranta.ui.havaintolomake :refer [havaintolomake]]
            [harja-laadunseuranta.ui.tarkastusajon-paattaminen :as tarkastusajon-paattaminen]
            [cljs.core.async :refer [<! timeout]]
            [harja-laadunseuranta.ui.yleiset.varmistusdialog :as varmistusdialog])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- spinneri [lahettamattomia]
  (when (> @lahettamattomia 0)
    [:img.spinner {:src kuvat/+spinner+}]))

(defn- paanakyma []
  [:div.toplevel
   [kamera/file-input
    #(kamera-tiedot/kuva-otettu % s/kuvaa-otetaan?)]
   [ylapalkki/ylapalkki]

   (when @s/varmistusdialog-nakyvissa?
     [varmistusdialog/varmistusdialog-komponentti @s/varmistusdialog-data])

   [:div.paasisalto-container
    [kartta/kartta]

    (when @s/piirra-paanavigointi?
      [paanavigointi])

    [ilmoitukset/ilmoituskomponentti
     {:ilmoitus-atom s/ilmoitus
      :lomakedata @s/havaintolomakedata
      :havainnon-id @s/ilmoitukseen-liittyva-havainto-id
      :taydenna-havaintoa-painettu-fn ilmoitukset-tiedot/ilmoitusta-painettu!
      :ilmoitukseen-liittyva-havainto-id-atom s/ilmoitukseen-liittyva-havainto-id}]

    (when @s/havaintolomake-auki?
      [havaintolomake])

    (when @s/tarkastusajo-paattymassa?
      [tarkastusajon-paattaminen/tarkastusajon-paattamiskomponentti
       @s/tarkastusajon-paattamisvaihe])

    (when (and @s/palautettava-tarkastusajo (not (= "?relogin=true" js/window.location.search)))
      [tarkastusajon-paattaminen/tarkastusajon-jatkamiskomponentti])

    [spinneri s/lahettamattomia-merkintoja]]])

(defn- maarita-alustuksen-tila [alustustieto]
  (cond
    (nil? alustustieto) :tarkistetaan
    (true? alustustieto) :ok
    :default :virhe))

(defn main []
  (if (and @s/alustus-valmis? @s/sovelluksen-naytto-sallittu?)
    [paanakyma]
    [alustus/alustuskomponentti
     {:selain-tuettu (maarita-alustuksen-tila @s/selain-tuettu?)
      :selain-vanhentunut? @s/selain-vanhentunut
      :gps-tuettu (maarita-alustuksen-tila @s/gps-tuettu)
      :ensimmainen-sijainti-saatu (maarita-alustuksen-tila @s/ensimmainen-sijainti-saatu)
      :ensimmainen-sijainti-virhekoodi @s/ensimmainen-sijainti-virhekoodi
      :oikeus-urakoihin (maarita-alustuksen-tila @s/kayttajalla-oikeus-ainakin-yhteen-urakkaan)
      :idxdb-tuettu (maarita-alustuksen-tila @s/idxdb-tuettu)
      :kayttaja-tunnistettu (maarita-alustuksen-tila @s/kayttaja-tunnistettu)
      :verkkoyhteys (maarita-alustuksen-tila @s/verkkoyhteys)}]))
