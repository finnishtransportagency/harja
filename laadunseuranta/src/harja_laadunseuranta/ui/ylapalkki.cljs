(ns harja-laadunseuranta.ui.ylapalkki
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.tiedot.ylapalkki :as tiedot]
            [harja-laadunseuranta.tiedot.kamera :as kamera]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as tarkastusajon-luonti]
            [harja-laadunseuranta.tiedot.sovellus :as s]))

(defn- formatoi-tr-osoite [tr-osoite]
  (let [{:keys [tie aosa aet]} tr-osoite]
    (str (or tie "-") " / " (or aosa "-") " / " (or aet "-"))))

(defn- logo-klikattu []
  (when-not @s/tallennus-kaynnissa
    (set! (.-location js/window) asetukset/+harja-url+)))

(defn- logo []
  [:div.logo
   (when (or (utils/kehitysymparistossa?)
             (utils/stg-ymparistossa?))
     [:span#testiharja "TESTI"])
   [:picture {:on-click logo-klikattu}
    [:source {:srcSet kuvat/+harja-logo-ilman-tekstia+ :type "image/svg+xml"
              :media "(max-width: 700px)"}]
    [:img {:src kuvat/+harja-logo+ :alt ""}]]])

(defn- havaintosilma [havaintonappi-painettu]
  [:div {:class (str "ylapalkki-button ylapalkki-button-nayta-paanavigointi livicon-eye "
                     (when (and @s/nayta-paanavigointi?
                                @s/piirra-paanavigointi?)
                       "ylapalkki-button-aktiivinen ")
                     (when-not @s/tallennus-kaynnissa
                       "ylapalkki-button-disabloitu "))
         :on-click havaintonappi-painettu}])

(defn- tieosoite [tr-osoite]
  [:div.tr-osoite (formatoi-tr-osoite @tr-osoite)])

(defn- metatiedot [soratiehoitoluokka hoitoluokka]
  [:div.ylapalkin-metatiedot
   [:div.ylapalkin-metatieto.soratiehoitoluokka (str "SHL: " (or @soratiehoitoluokka "-"))]
   [:div.ylapalkin-metatieto.talvihoitoluokka (str "THL: " (or @hoitoluokka "-"))]])

(defn- kaynnistyspainike [{:keys [tallennus-kaynnissa kaynnista-fn
                                  pysayta-fn aloitetaan-tarkastusajo]}]
  [:div.kaynnistyspainike {:class (when @tallennus-kaynnissa "kaynnissa")
                           :on-click (if @tallennus-kaynnissa
                                       pysayta-fn
                                       kaynnista-fn)}
   (when-not @aloitetaan-tarkastusajo [:span.kaynnistyspainike-nuoli.livicon-arrow-start])
   [:span.kaynnistyspainike-teksti
    (if @tallennus-kaynnissa
      "Pysäytä tarkastus"
      (if @aloitetaan-tarkastusajo
        "Käynnistetään..."
        "Käynnistä tarkastus"))]])

(defn- kamera []
  [:div.ylapalkki-button {:on-click kamera/ota-kuva}
   [:span.glyphicon.glyphicon-camera]])

(defn- ylapalkkikomponentti [{:keys [hoitoluokka soratiehoitoluokka
                                     tr-osoite tallennus-kaynnissa aloitetaan-tarkastusajo
                                     kaynnista-tarkastus-fn pysayta-tarkastusajo-fn
                                     disabloi-kaynnistys? havaintonappi-painettu
                                     palvelinvirhe]}]
  [:div
   [:div.ylapalkki {:class (when (or (utils/kehitysymparistossa?)
                                     (utils/stg-ymparistossa?)) "testiharja")}
    [:div.ylapalkki-vasen
     [logo]
     [havaintosilma havaintonappi-painettu]
     [kamera]
     [tieosoite tr-osoite]
     [metatiedot soratiehoitoluokka hoitoluokka]]
    [:div.ylapalkki-oikea
     [kaynnistyspainike {:tallennus-kaynnissa tallennus-kaynnissa
                         :kaynnista-fn #(when (and (not disabloi-kaynnistys?)
                                                   (not @aloitetaan-tarkastusajo))
                                          (kaynnista-tarkastus-fn))
                         :pysayta-fn #(when-not disabloi-kaynnistys?
                                        (pysayta-tarkastusajo-fn))
                         :aloitetaan-tarkastusajo aloitetaan-tarkastusajo}]]]
   (when @palvelinvirhe [:div.palvelinvirhe "Palvelinvirhe: " @palvelinvirhe])])

(defn ylapalkki []
  [ylapalkkikomponentti
   {:hoitoluokka s/hoitoluokka
    :havaintonappi-painettu tiedot/havaintonappi-painettu!
    :aloitetaan-tarkastusajo s/aloitetaan-tarkastusajo
    :soratiehoitoluokka s/soratiehoitoluokka
    :kaynnista-tarkastus-fn tarkastusajon-luonti/luo-ajo!
    :pysayta-tarkastusajo-fn tarkastusajon-luonti/aseta-ajo-paattymaan!
    :tr-osoite s/tr-osoite
    :tallennus-kaynnissa s/tallennus-kaynnissa
    :disabloi-kaynnistys? (or @s/havaintolomake-auki
                              @s/palautettava-tarkastusajo)
    :palvelinvirhe s/palvelinvirhe}])