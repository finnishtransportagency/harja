(ns harja.palvelin.integraatiot.sampo.sanomat.kustannussuunnitelma-sanoma
  (:require [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.periodic :as time-period]
            [clj-time.coerce :as coerce]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn muodosta-maksueranumero [numero]
  (str/join "" ["HA" numero]))

(defn muodosta-kustannussuunnitelmanumero [numero]
  (str/join "" ["AK" numero]))

(defn muodosta-kustannuselementti [alkupvm loppupvm summa]
  [:segment
   {:value  summa
    :finish loppupvm
    :start  alkupvm}])

(defn aikavali
  [alku loppu askel]
  (let [vali (time-period/periodic-seq alku askel)
        valilla? (fn [aika] (time/within? (time/interval alku loppu) aika))]
    (take-while valilla? vali)))

(defn muodosta-ensimmaisen-vuoden-elementti [alkupvm kuukausittainen-summa ensimmaisen-vuoden-kuukaudet]
  (let [loppupvm (pvm/aika-iso8601 (pvm/vuoden-viim-pvm (time/year alkupvm)))
        summa (* kuukausittainen-summa ensimmaisen-vuoden-kuukaudet)]
    (muodosta-kustannuselementti alkupvm loppupvm summa)))

(defn muodosta-valivuosien-elementit [alkuvuosi taysien-vuosien-maara kuukausittainen-summa]
  (concat
    (into []
          (for [i (range taysien-vuosien-maara)]
            (do
              (inc i)
              (let [vuosi (+ 1 i alkuvuosi)
                    alkupvm (pvm/aika-iso8601 (pvm/vuoden-eka-pvm vuosi))
                    loppupvm (pvm/aika-iso8601 (pvm/vuoden-viim-pvm vuosi))
                    summa (* 12 kuukausittainen-summa)]
                (muodosta-kustannuselementti alkupvm loppupvm summa)))))))

(defn muodosta-viimeisen-vuoden-elementti [loppupvm kuukausittainen-summa viimeisen-vuoden-kuukaudet]
  (let [alkupvm (pvm/aika-iso8601 (pvm/vuoden-eka-pvm (time/year loppupvm)))
        summa (* kuukausittainen-summa viimeisen-vuoden-kuukaudet)]
    (muodosta-kustannuselementti alkupvm loppupvm summa)))

(defn luo-summat [alkupvm loppupvm maksuera]
  (let [alkupvm (coerce/from-sql-date alkupvm)
        _ (println alkupvm)
        loppupvm (coerce/from-sql-date loppupvm)
        kuukausien-maara (count (aikavali alkupvm loppupvm (time/months 1)))
        _ (println kuukausien-maara)
        summa (:summa (:kustannussuunnitelma maksuera))
        summa (if summa (double summa) 0)
        kuukausittainen-summa (if (and (< 0 kuukausien-maara) summa (< 0 summa))
                                (/ summa kuukausien-maara)
                                0)
        ensimmaisen-vuoden-kuukaudet (time/month alkupvm)
        viimeisen-vuoden-kuukaudet (rem (- kuukausien-maara ensimmaisen-vuoden-kuukaudet) 12)
        taysien-vuosien-maara (- kuukausien-maara viimeisen-vuoden-kuukaudet)
        kustannuselementit []
        alkuvuosi (time/year alkupvm)
        loppuvuosi (time/year loppupvm)]

    (into [] (-> kustannuselementit
                 ;; ensimmäinen vuosi
                 (concat [(muodosta-ensimmaisen-vuoden-elementti alkupvm kuukausittainen-summa ensimmaisen-vuoden-kuukaudet)])

                 ;; välissä olevat täydet vuodet
                 (when (> 0 taysien-vuosien-maara)
                       (muodosta-valivuosien-elementit alkuvuosi taysien-vuosien-maara kuukausittainen-summa))

                 ;; viimeinen vuosi
                 (when (< alkuvuosi loppuvuosi)
                       (concat [(muodosta-viimeisen-vuoden-elementti loppupvm kuukausittainen-summa viimeisen-vuoden-kuukaudet)]))))))

(defn muodosta-custom-information [nimi arvo]
  [:CustomInformation
   [:ColumnValue
    {:name nimi}
    arvo]])

(defn muodosta-grouping-attribute [koodi arvo]
  [:GroupingAttribute
   {:value arvo
    :code  koodi}])

(defn valitse-lkp-tilinumero [toimenpidekoodi tuotenumero]
  (if (or (= toimenpidekoodi "20112") (= toimenpidekoodi "20143") (= toimenpidekoodi "20179"))
    "43021"
    ; Hoitotuotteet 110 - 150, 536
    (if (nil? tuotenumero)
      (let [viesti "Tuotenumero on tyhjä. LPK-tilinnumeroa ei voi päätellä. Kustannussuunnitelman lähetys epäonnistui."]
        (log/error viesti)
        (throw+ {:type    :virhe-sampo-kustannussuunnitelman-lahetyksessa
                 :virheet [{:koodi  :lpk-tilinnumeroa-ei-voi-paatella
                            :viesti viesti}]}))

      (if (or (and (>= tuotenumero 110) (<= tuotenumero 150))
              (= tuotenumero 536)
              (= tuotenumero 31))
        "43021"
        ; Ostotuotteet: 210, 240-271 ja 310-321
        (if (or (= tuotenumero 21)
                (= tuotenumero 30)
                (= tuotenumero 210)
                (and (>= tuotenumero 240) (<= tuotenumero 271))
                (and (>= tuotenumero 310) (<= tuotenumero 321)))
          "12981"
          (let [viesti
                (format "Toimenpidekoodilla '%1$s' ja tuonenumerolla '%2$s' ei voida päätellä LKP-tilinnumeroa kustannussuunnitelmalle"
                        toimenpidekoodi tuotenumero)]
            (log/error viesti)
            (throw+ {:type    :virhe-sampo-kustannussuunnitelman-lahetyksessa
                     :virheet [{:koodi  :lpk-tilinnumeroa-ei-voi-paatella
                                :viesti viesti}]})))))))

(defn tee-kustannussuunnitelmajakso [pvm]
  (let [vuosi (time/year (coerce/from-sql-date pvm))]
    (str "1.1." vuosi "-31.12." vuosi)))

(defn muodosta [maksuera]
  (let [{:keys [alkupvm loppupvm]} (:toimenpideinstanssi maksuera)
        koodi (:toimenpidekoodi maksuera)
        tuotenumero (:tuotenumero maksuera)
        maksueranumero (muodosta-maksueranumero (:numero maksuera))
        kustannussuunnitelmanumero (muodosta-kustannussuunnitelmanumero (:numero maksuera))]
    [:NikuDataBus
     [:Header
      {:objectType     "costPlan"
       :action         "write"
       :externalSource "NIKU"
       :version        "13.1.0.0248"}]
     [:CostPlans
      [:CostPlan
       {:finishPeriod   (tee-kustannussuunnitelmajakso loppupvm)
        :startPeriod    (tee-kustannussuunnitelmajakso alkupvm)
        :periodType     "ANNUALLY"
        :investmentType "PRODUCT"
        :investmentCode maksueranumero
        :name           (apply str (take 80 (:nimi (:maksuera maksuera))))
        :code           kustannussuunnitelmanumero
        :isPlanOfRecord "true"}
       [:Description ""]
       [:GroupingAttributes
        [:GroupingAttribute "role_id"]
        [:GroupingAttribute "lov1_id"]]
       [:Details
        [:Detail
         (luo-summat alkupvm loppupvm maksuera)
         [:GroupingAttributes
          (muodosta-grouping-attribute "lov1_id" "3110201")
          (muodosta-grouping-attribute "role_id" (valitse-lkp-tilinumero koodi tuotenumero))]
         (muodosta-custom-information "vv_vat_code" "L024")]]
       (muodosta-custom-information "vv_purpose" "5")]]]))
