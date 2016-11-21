(ns harja-laadunseuranta.ui.ylapalkki
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.utils :as utils]
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

(defn- kaynnistyspainike [tallennus-kaynnissa kaynnista-fn pysayta-fn]
  [:div.kaynnistyspainike {:class (when @tallennus-kaynnissa "kaynnissa")
                           :on-click (if @tallennus-kaynnissa
                                       pysayta-fn
                                       kaynnista-fn)}
   [:span.kaynnistyspainike-nuoli.livicon-arrow-start]
   [:span.kaynnistyspainike-teksti
    (if @tallennus-kaynnissa
      "Pysäytä tarkastus"
      "Käynnistä tarkastus")]])

(defn- keskityspainike [keskita-ajoneuvoon]
  [:div.ylapalkki-button.keskityspainike.livicon-crosshairs {:on-click #(do (swap! keskita-ajoneuvoon not)
                                                                            (swap! keskita-ajoneuvoon not))}])

(defn- ylapalkkikomponentti [{:keys [tiedot-nakyvissa hoitoluokka soratiehoitoluokka
                                     tr-osoite kiinteistorajat ortokuva
                                     tallennus-kaynnissa
                                     kaynnista-tarkastus-fn pysayta-tarkastusajo-fn
                                     keskita-ajoneuvoon disabloi-kaynnistys? valittu-urakka
                                     palvelinvirhe]}]
  [:div
   [:div.ylapalkki {:class (when (or (utils/kehitysymparistossa?)
                                     (utils/stg-ymparistossa?)) "testiharja")}
    [:div.ylapalkki-vasen
     [logo]
     [:div#karttakontrollit]
     (when-not @tallennus-kaynnissa
       [keskityspainike keskita-ajoneuvoon])
     [:div.ylapalkki-button.kiinteistorajat.livicon-home {:on-click #(swap! kiinteistorajat not)}]
     [:div.ylapalkki-button.ortokuva.livicon-eye {:on-click #(swap! ortokuva not)}]
     [:div.ylapalkki-button.infonappi.livicon-circle-info {:on-click #(swap! tiedot-nakyvissa not)}]
     [:div.tr-osoite (formatoi-tr-osoite @tr-osoite)]
     [:div.ylapalkin-metatiedot
      [:div.ylapalkin-metatieto.urakkanimi {:on-click #(println "TODO: tästäkin vaihtuu urakka")}
       (if @valittu-urakka
         (utils/lyhennetty-urakan-nimi (:nimi @valittu-urakka))
         "")]
      [:div.ylapalkin-metatieto.soratiehoitoluokka (str "SHL: " (or @soratiehoitoluokka "-"))]
      [:div.ylapalkin-metatieto.talvihoitoluokka (str "THL: " (or @hoitoluokka "-"))]]]
    [:div.ylapalkki-oikea
     [kaynnistyspainike tallennus-kaynnissa
      #(when-not disabloi-kaynnistys?
        (kaynnista-tarkastus-fn))
      #(when-not disabloi-kaynnistys?
        (pysayta-tarkastusajo-fn))]]]
   (when @palvelinvirhe [:div.palvelinvirhe "Palvelinvirhe: " @palvelinvirhe])])

(defn ylapalkki []
  [ylapalkkikomponentti
   {:tiedot-nakyvissa s/tr-tiedot-nakyvissa
    :hoitoluokka s/hoitoluokka
    :soratiehoitoluokka s/soratiehoitoluokka
    :kaynnista-tarkastus-fn tarkastusajon-luonti/luo-ajo!
    :pysayta-tarkastusajo-fn tarkastusajon-luonti/aseta-ajo-paattymaan!
    :tr-osoite s/tr-osoite
    :kiinteistorajat s/nayta-kiinteistorajat
    :ortokuva s/nayta-ortokuva
    :tallennus-kaynnissa s/tallennus-kaynnissa
    :keskita-ajoneuvoon s/keskita-ajoneuvoon
    :disabloi-kaynnistys? (or @s/havaintolomake-auki
                              @s/palautettava-tarkastusajo)
    :valittu-urakka s/valittu-urakka
    :palvelinvirhe s/palvelinvirhe}])