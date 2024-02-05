(ns harja.domain.kanavat.raportointi
  (:require
   [clojure.string :as str]))

(defn kokoa-lyhytnimet [data]
  (->> data
    (map #(or (:lyhyt_nimi %) (:nimi %)))
    (str/join ", ")))

(defn suodata-urakat [data idt]
  (filter #(idt (:id %)) data))
