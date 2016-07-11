(ns harja.palvelin.integraatiot.api.sanomat.paallystysilmoitus
  (:require [cheshire.core :as cheshire]))

(defn rakenna-alikohteet [paallystysilmoitus]
  (mapv (fn [alikohde]
          (let [kivi (:kivi-ja-sideaine (first (:kivi-ja-sideaineet alikohde)))]
            {:kohdeosa-id (:id alikohde)
             :rc% (:rc-prosentti alikohde)
             :leveys (:leveys alikohde)
             :km-arvo (:km-arvo kivi)
             :raekoko (:raekoko alikohde)
             :pinta-ala (:pinta-ala alikohde)
             :esiintyma (:esiintyma kivi)
             :muotoarvo (:muotoarvo kivi)
             :pitoisuus (:pitoisuus kivi)
             ;; todo: mäppää selitteistä koodeiksi
             :kuulamylly (:kuulamylly kivi)
             :lisaaineet (:lisa-aineet kivi)
             :massamenekki (:massamenekki alikohde)
             :tyomenetelma (:tyomenetelma alikohde)
             :sideainetyyppi (:sideainetyyppi kivi)
             :paallystetyyppi (:paallystetyyppi alikohde)
             :kokonaismassamaara (:kokonaismassamaara alikohde)
             :edellinen-paallystetyyppi (:edellinen-paallystetyyppi alikohde)}))
        (get-in paallystysilmoitus [:yllapitokohde :alikohteet])))


(defn rakenna-alustatoimet [paallystysilmoitus]
  (mapv (fn [alustatoimi]
          (let [sijainti (:sijainti alustatoimi)]
            {:aosa (:aosa sijainti)
             :aet (:aet sijainti)
             :losa (:losa sijainti)
             :let (:let sijainti)
             :paksuus (:paksuus alustatoimi)
             ;; todo: mäppää selitteistä koodeiksi
             :verkkotyyppi (:verkkotyyppi alustatoimi)
             :verkon-sijainti (:verkon-sijainti alustatoimi)
             :verkon-tarkoitus (:verkon-tarkoitus alustatoimi)
             :kasittelymenetelma (:kasittelymenetelma alustatoimi)
             :tekninen-toimenpide (:tekninen-toimenpide alustatoimi)}))
        (:alustatoimenpiteet paallystysilmoitus)))

(defn rakenna-tyot [paallystysilmoitus]
  (mapv (fn [tyo]
          {;; todo: mäppää selitteistä koodeiksi
           :tyo (:tyotehtava tyo)
           :tyyppi (:tyyppi tyo)
           :yksikko (:yksikko tyo)
           :yksikkohinta (:yksikkohinta tyo)
           :tilattu-maara (:tilattu-maara tyo)
           :toteutunut-maara (:tilattu-maara tyo)})
        (:tyot paallystysilmoitus)))

(defn rakenna [paallystysilmoitus]
  (let [data {:osoitteet (rakenna-alikohteet paallystysilmoitus)
              :alustatoimet (rakenna-alustatoimet paallystysilmoitus)
              :tyot (rakenna-tyot paallystysilmoitus)}]
    (cheshire/encode data)))