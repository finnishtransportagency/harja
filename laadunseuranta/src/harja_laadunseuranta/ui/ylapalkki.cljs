(ns harja-laadunseuranta.ui.ylapalkki
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.tiedot.ylapalkki :as tiedot]
            [harja-laadunseuranta.tiedot.kamera :as kamera]
            [harja-laadunseuranta.tiedot.tarkastusajon-luonti :as tarkastusajon-luonti]
            [harja-laadunseuranta.tiedot.tarkastusajon-paattaminen :as tarkastusajon-paattaminen]
            [harja-laadunseuranta.tiedot.sovellus :as s]))

(defn- formatoi-tr-osoite [tr-osoite]
  (let [{:keys [tie aosa aet]} tr-osoite]
    (str (or tie "-") " / " (or aosa "-") " / " (or aet "-"))))

(defn- logo-klikattu []
  (when-not @s/tarkastusajo-kaynnissa?
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

(defn- havaintosilma [{:keys [havaintonappi-painettu
                              nayta-paanavigointi?
                              piirra-paanavigointi?
                              havaintolomake-auki?
                              tarkastusajo-kaynnissa?]}]
  (let [aktiivinen? (and nayta-paanavigointi?
                         piirra-paanavigointi?)
        disabloitu? (or (not tarkastusajo-kaynnissa?)
                        havaintolomake-auki?)]
    [:div {:class (str "ylapalkki-button ylapalkki-button-nayta-paanavigointi "
                       (when aktiivinen?
                         "ylapalkki-button-aktiivinen ")
                       (when disabloitu?
                         "ylapalkki-button-disabloitu "))
           :on-click #(when-not disabloitu?
                        (havaintonappi-painettu %))}
     [kuvat/svg-sprite "silma-24"]]))

(defn- tieosoite [tr-osoite]
  [:div.tr-osoite (formatoi-tr-osoite tr-osoite)])

(defn- metatiedot [soratiehoitoluokka hoitoluokka]
  [:div.ylapalkin-metatiedot
   [:div.ylapalkin-metatieto.soratiehoitoluokka (str "SHL: " (or soratiehoitoluokka "-"))]
   [:div.ylapalkin-metatieto.talvihoitoluokka (str "THL: " (or hoitoluokka "-"))]])

(defn- kaynnistyspainike [{:keys [tarkastusajo-kaynnissa? kaynnista-fn
                                  pysayta-fn tarkastusajo-alkamassa?]}]
  [:div.kaynnistyspainike {:class (when tarkastusajo-kaynnissa? "kaynnissa")
                           :on-click (if tarkastusajo-kaynnissa?
                                       pysayta-fn
                                       kaynnista-fn)}
   (when-not tarkastusajo-alkamassa?
     [kuvat/svg-sprite "nuoli-ylos-alaviiva-24"])
   [:span.kaynnistyspainike-teksti
    (if tarkastusajo-kaynnissa?
      "Pysäytä tarkastus"
      (if tarkastusajo-alkamassa?
        "Käynnistetään..."
        "Käynnistä tarkastus"))]])

(defn- kamera [tarkastusajo-kaynnissa? kuvaa-otetaan-atom]
  (let [disabloitu? (not tarkastusajo-kaynnissa?)]
    [:div {:class (str "ylapalkki-button "
                       (when disabloitu?
                         "ylapalkki-button-disabloitu "))
           :on-click #(when-not disabloitu?
                        (kamera/ota-kuva kuvaa-otetaan-atom))}
     [kuvat/svg-sprite "kamera-24"]]))

(defn- ylapalkkikomponentti [{:keys [hoitoluokka soratiehoitoluokka nayta-paanavigointi?
                                     tr-osoite tarkastusajo-kaynnissa? tarkastusajo-alkamassa?
                                     kaynnista-tarkastus-fn pysayta-tarkastusajo-fn
                                     disabloi-kaynnistys? havaintonappi-painettu
                                     kuvaa-otetaan-atom
                                     palvelinvirhe piirra-paanavigointi? havaintolomake-auki?]}]
  [:div
   [:div.ylapalkki {:class (when (or (utils/kehitysymparistossa?)
                                     (utils/stg-ymparistossa?)) "testiharja")}
    [:div.ylapalkki-vasen
     [logo]
     [havaintosilma {:havaintonappi-painettu havaintonappi-painettu
                     :nayta-paanavigointi? nayta-paanavigointi?
                     :piirra-paanavigointi? piirra-paanavigointi?
                     :havaintolomake-auki? havaintolomake-auki?
                     :tarkastusajo-kaynnissa? tarkastusajo-kaynnissa?}]
     [kamera tarkastusajo-kaynnissa? kuvaa-otetaan-atom]
     [tieosoite tr-osoite]
     [metatiedot soratiehoitoluokka hoitoluokka]]
    [:div.ylapalkki-oikea
     [kaynnistyspainike {:tarkastusajo-kaynnissa? tarkastusajo-kaynnissa?
                         :kaynnista-fn #(when (and (not disabloi-kaynnistys?)
                                                   (not tarkastusajo-alkamassa?))
                                          (kaynnista-tarkastus-fn))
                         :pysayta-fn #(when-not disabloi-kaynnistys?
                                        (pysayta-tarkastusajo-fn))
                         :tarkastusajo-alkamassa? tarkastusajo-alkamassa?}]]]
   (when palvelinvirhe [:div.palvelinvirhe "Palvelinvirhe: " palvelinvirhe])])

(defn ylapalkki []
  [ylapalkkikomponentti
   {:hoitoluokka @s/hoitoluokka
    :havaintonappi-painettu tiedot/havaintonappi-painettu!
    :nayta-paanavigointi? @s/nayta-paanavigointi?
    :piirra-paanavigointi? @s/piirra-paanavigointi?
    :havaintolomake-auki? @s/havaintolomake-auki?
    :kuvaa-otetaan-atom s/kuvaa-otetaan?
    :tarkastusajo-alkamassa? @s/tarkastusajo-alkamassa?
    :soratiehoitoluokka @s/soratiehoitoluokka
    :kaynnista-tarkastus-fn tarkastusajon-luonti/luo-ajo!
    :pysayta-tarkastusajo-fn tarkastusajon-paattaminen/aseta-ajo-paattymaan!
    :tr-osoite @s/tr-osoite
    :tarkastusajo-kaynnissa? @s/tarkastusajo-kaynnissa?
    :disabloi-kaynnistys? (or @s/havaintolomake-auki?
                              @s/palautettava-tarkastusajo)
    :palvelinvirhe @s/palvelinvirhe}])