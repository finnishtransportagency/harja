(ns harja-laadunseuranta.ylapalkki
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.asetukset :as asetukset]
            [harja-laadunseuranta.kuvat :as kuvat]
            [harja-laadunseuranta.utils :as utils]))

(defn- formatoi-tr-osoite [tr-osoite]
  (let [{:keys [tie aosa aet]} tr-osoite]
    (str (or tie "-") " / " (or aosa "-") " / " (or aet "-"))))

(defn logo []
  [:img {:class "logo"
         :on-click #(set! (.-location js/window) asetukset/+harja-url+)
         :src kuvat/+harja-logo+}])

(defn kaynnistyspainike [tallennus-kaynnissa tallennustilaa-muutetaan disabloi?]
  [:div.kaynnistyspainike {:class (when @tallennus-kaynnissa "kaynnissa")
                           :on-click #(when-not @disabloi?
                                        (do
                                          (reset! tallennustilaa-muutetaan true)
                                          (reset! tallennus-kaynnissa false)))}
   [:span.livicon-arrow-start]
   (if @tallennus-kaynnissa
     "Pysäytä tarkastus"
     "Käynnistä tarkastus")])

(defn keskityspainike [keskita-ajoneuvoon]
  [:div.ylapalkki-button.keskityspainike.livicon-crosshairs {:on-click #(do (swap! keskita-ajoneuvoon not)
                                                                            (swap! keskita-ajoneuvoon not))}])

(defn ylapalkkikomponentti [{:keys [tiedot-nakyvissa hoitoluokka soratiehoitoluokka
                                    tr-osoite kiinteistorajat ortokuva
                                    tallennus-kaynnissa tallennustilaa-muutetaan
                                    keskita-ajoneuvoon disabloi-kaynnistys? valittu-urakka]}]
  [:div.ylapalkki
   (when (utils/kehitysymparistossa?)
     [:span#testiharja "TESTI"])
   [logo]
   (when-not @tallennus-kaynnissa
     [keskityspainike keskita-ajoneuvoon])
   [:div.tr-osoite (formatoi-tr-osoite @tr-osoite)]
   [:div.ylapalkin-metatiedot

    [:div.urakkanimi {:on-click #(println "TODO: tästäkin vaihtuu urakka")}
     (if @valittu-urakka
       (utils/lyhennetty-urakan-nimi (:nimi @valittu-urakka))
       "")]
    [:div.soratiehoitoluokka (str "SHL: " (or @soratiehoitoluokka "-"))]
    [:div.talvihoitoluokka (str "THL: " (or @hoitoluokka "-"))]]
   [:div.ylapalkki-button.kiinteistorajat.livicon-home {:on-click #(swap! kiinteistorajat not)}]
   [:div.ylapalkki-button.ortokuva.livicon-eye {:on-click #(swap! ortokuva not)}]
   [:div.ylapalkki-button.infonappi.livicon-circle-info {:on-click #(swap! tiedot-nakyvissa not)}]
   [kaynnistyspainike tallennus-kaynnissa tallennustilaa-muutetaan disabloi-kaynnistys?]])
