(ns harja.palvelin.komponentit.komponenttien-tila-test
  (:require [clojure.test :refer :all]
            [clojure.set :as clj-set]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.komponentit.komponenttien-tila :as komponenttien-tila]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]
            [clojure.core.async :as async]))

(def kaikkien-tapahtumien-kuuntelija nil)

(def tila (atom nil))

(defn tallenna-tapahtuma [tapahtuma data]
  (swap! tila assoc tapahtuma data))

(defn tarkkailija-jarjestelma [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :komponenttien-tila (komponenttien-tila/komponentin-tila {:itmf {:paivitystiheys-ms 5000}
                                                                                  :db {:paivitystiheys-ms 5000}
                                                                                  :db-replica {:paivitystiheys-ms 5000}})))))
  (alter-var-root #'kaikkien-tapahtumien-kuuntelija
                  (fn [_]
                    (zipmap [::harja-kuuntelija ::harjajarjestelman-restart ::harjajarjestelman-restart-onnistui ::harjajarjestelman-restart-epaonnistui ::jms-kuuntelija ::jms-uudelleenkaynnistys-epaonnistui ::db-kuuntelija ::db-replica-kuuntelija]
                            (tapahtuma-apurit/tarkkaile-tapahtumia :harja-tila {:tyyppi :perus} (partial tallenna-tapahtuma :harja-tila)
                                                                   :harjajarjestelman-restart {:tyyppi :perus} (partial tallenna-tapahtuma :harjajarjestelman-restart)
                                                                   :harjajarjestelman-restart-onnistui {:tyyppi :perus} (partial tallenna-tapahtuma :harjajarjestelman-restart-onnistui)
                                                                   :harjajarjestelman-restart-epaonnistui {:tyyppi :perus} (partial tallenna-tapahtuma :harjajarjestelman-restart-epaonnistui)
                                                                   :jms-tila {:tyyppi :perus} (partial tallenna-tapahtuma :jms-tila)
                                                                   :jms-uudelleenkaynnistys-epaonnistui {:tyyppi :perus} (partial tallenna-tapahtuma :jms-uudelleenkaynnistys-epaonnistui)
                                                                   :db-tila {:tyyppi :perus} (partial tallenna-tapahtuma :db-tila)
                                                                   :db-replica-tila {:tyyppi :perus} (partial tallenna-tapahtuma :db-replica-tila)))))
  (doseq [[_ tarkkailija] kaikkien-tapahtumien-kuuntelija]
    @tarkkailija)
  (doseq [[avain tarkkailija] (get jarjestelma :komponenttien-tila)
          :when (not (nil? (namespace avain)))]
    @tarkkailija)
  (testit)
  (doseq [[_ tarkkailija] kaikkien-tapahtumien-kuuntelija]
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @tarkkailija))
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(use-fixtures :each tarkkailija-jarjestelma)

(def tapahtumat {:harja-tila {::ok {:viesti "Harja käynnistetty"
                                    :kaikki-ok? true}
                              ::ei-ok {:viesti "Harja käynnistyy"
                                       :kaikki-ok? false}}
                 :harjajarjestelman-restart {::ok #{}}
                 :harjajarjestelman-restart-onnistui {::ok tapahtuma-tulkkaus/tyhja-arvo}
                 :harjajarjestelman-restart-epaonnistui {::ok tapahtuma-tulkkaus/tyhja-arvo}
                 :jms-tila {::ok {}
                            ::ei-ok {}
                            ::virhe {:virhe nil}}
                 :jms-uudelleenkaynnistys-epaonnistui {::ok ""}
                 :db-tila {::ok true
                           ::ei-ok false}
                 :db-replica-tila {::ok true
                                   ::ei-ok false}})

(deftest ok-tapahtumat-satunnaisessa-jarjestyksessa
  (let [ok-tapahtumat (map (fn [[tapahtuma vaihtoehdot]]
                             [tapahtuma (::ok vaihtoehdot)])
                           (dissoc tapahtumat :harjajarjestelman-restart-epaonnistui :jms-uudelleenkaynnistys-epaonnistui))
        satunnainen-sarja-tapahtumia (loop [sarja []
                                            sisaltaa-kaikki? (= (into #{} ok-tapahtumat) (into #{} sarja))]
                                       (if (or sisaltaa-kaikki?
                                               (> (count sarja) 100))
                                         sarja
                                         (recur (conj sarja (rand-nth ok-tapahtumat))
                                                (= (into #{} ok-tapahtumat) (into #{} sarja)))))
        ok-tapahtumien-nimet (into #{} (map first) ok-tapahtumat)
        kaikki-ok? (fn [{:keys [harja db db-replica]}]
                     (and
                       ;; harja tapahtumaa ei annettu tai sitten harja on ok
                       (or (empty? (clj-set/intersection ok-tapahtumien-nimet #{:harja-tila :harjajarjestelman-restart :harjajarjestelman-restart-onnistui}))
                           (:kaikki-ok? harja))
                       ;; db tapahtumaa ei annettu tai sitten db on ok
                       (or (empty? (clj-set/intersection ok-tapahtumien-nimet #{:db-tila}))
                           (:kaikki-ok? db))
                       ;; replica tapahtumaa ei annettu tai sitten replica on ok
                       (or (empty? (clj-set/intersection ok-tapahtumien-nimet #{:db-replica-tila}))
                           (:kaikki-ok? db-replica))))]
    (doseq [[tapahtuma tapahtuman-data] satunnainen-sarja-tapahtumia]
      (tapahtuma-apurit/julkaise-tapahtuma tapahtuma tapahtuman-data))
    (odota-ehdon-tayttymista (fn []
                               (clj-set/subset? ok-tapahtumien-nimet (into #{} (keys @tila))))
                             (str "Tapahtumia " ok-tapahtumien-nimet " ei julkaistu ajallaan")
                             5000)
    (async/<!! (async/timeout 700))
    (is (-> jarjestelma (get-in [:komponenttien-tila :komponenttien-tila]) deref (get tapahtuma-apurit/host-nimi) kaikki-ok?)
        (str "Kaikki ei ok tapahtumasarjalle: " (mapv first satunnainen-sarja-tapahtumia)))))
