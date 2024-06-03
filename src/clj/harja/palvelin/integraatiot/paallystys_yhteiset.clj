(ns harja.palvelin.integraatiot.paallystys-yhteiset 
  (:require [clojure.set :as set]
            [harja.kyselyt.konversio :as konversio]))

(defn- nimea-avaimet [toimenpide avaimet json?]
  (if json?
   (set/rename-keys toimenpide avaimet)
   (set/rename-keys toimenpide {:massat :massa})))

(defn muodosta-kulutuskerrostoimenpide [kulutuskerrostoimenpide json?]
  (-> kulutuskerrostoimenpide
    (update :massat #(-> (konversio/sarakkeet-vektoriin % {:runkoaine :runkoaineet
                                                           :lisaaine :lisaaineet
                                                           :sideaine :sideaineet})
                       (first)
                       (dissoc :id)))
    (update-in [:massat :runkoaineet] (fn [runkoaineet] (map #(dissoc % :id) runkoaineet)))
    (update-in [:massat :lisaaineet] (fn [lisaaineet] (map #(dissoc % :id) lisaaineet)))
    (update-in [:massat :sideaineet] (fn [sideaineet] (map #(dissoc % :id) sideaineet)))
    (update :massat (fn [massa] (when-not (nil? (:massatyyppi massa)) massa)))
    (nimea-avaimet {:pinta-ala :pintaAla
                    :massat :massa} json?)))

(defn muodosta-alustatoimenpide [alustatoimenpide json?]
  (-> alustatoimenpide
    (dissoc :id)
    (update :massat #(-> (konversio/sarakkeet-vektoriin % {:runkoaine :runkoaineet
                                                           :lisaaine :lisaaineet
                                                           :sideaine :sideaineet})
                       (first)
                       (dissoc :id)))
    (update-in [:massat :runkoaineet] (fn [runkoaineet] (map #(dissoc % :id) runkoaineet)))
    (update-in [:massat :lisaaineet] (fn [lisaaineet] (map #(dissoc % :id) lisaaineet)))
    (update-in [:massat :sideaineet] (fn [sideaineet] (map #(dissoc % :id) sideaineet)))
    (update :massat (fn [massa] (when-not (nil? (:massatyyppi massa)) massa)))
    (update :murske (fn [murske] (when-not (nil? (:tyyppi murske)) murske)))
    (nimea-avaimet {:pinta-ala :pintaAla
                    :lisatty-paksuus :lisattyPaksuus
                    :verkon-tyyppi :verkkotyyppi
                    :verkon-tarkoitus :verkonTarkoitus
                    :verkon-sijainti :verkonSijainti
                    :massat :massa} json?)))