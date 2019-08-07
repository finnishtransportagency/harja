(ns harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
  (:require [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.loki :refer [log]]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.tyokalut :as tyokalut]))


(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(def talvikausi [10 11 12 1 2 3 4])
(def kesakausi (into [] (range 5 10)))
(def hoitokausi (concat talvikausi kesakausi))

(def kaudet {:kesa kesakausi
             :talvi talvikausi
             :kaikki hoitokausi})

(defrecord HaeKustannussuunnitelma [])
(defrecord HaeTavoiteJaKattohinta [])
(defrecord HaeTavoiteJaKattohintaOnnistui [vastaus])
(defrecord HaeTavoiteJaKattohintaEpaonnistui [vastaus])
(defrecord LaajennaSoluaKlikattu [taulukon-polku rivin-id this auki?])
(defrecord PaivitaToimenpiteenHankintaMaara [osa arvo laskutuksen-perusteella-taulukko?])

(extend-protocol tuck/Event
  HaeTavoiteJaKattohinta
  (process-event [_ app]
    (let [urakka-id (-> @tiedot/tila :yleiset :urakka :id)]
      (tuck-apurit/post! app :budjettitavoite
                         {:urakka-id urakka-id}
                         {:onnistui ->HaeTavoiteJaKattohintaOnnistui
                          :epaonnistui ->HaeTavoiteJaKattohintaEpaonnistui
                          :paasta-virhe-lapi? true})))
  HaeTavoiteJaKattohintaOnnistui
  (process-event [{vastaus :vastaus} app]
    )
  HaeTavoiteJaKattohintaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    ;;TODO
    )
  LaajennaSoluaKlikattu
  (process-event [{:keys [taulukon-polku rivin-id auki?]} app]
    (update-in app taulukon-polku (fn [rivit]
                                               (mapv (fn [rivi]
                                                       (if (= (-> rivi meta :vanhempi) rivin-id)
                                                         (if auki?
                                                           (update rivi :luokat disj "piillotettu")
                                                           (update rivi :luokat conj "piillotettu"))
                                                         rivi))
                                                     rivit))))
  PaivitaToimenpiteenHankintaMaara
  (process-event [{:keys [osa arvo laskutuksen-perusteella-taulukko?]} app]
    (let [polku-taulukkoon (if laskutuksen-perusteella-taulukko?
                             [:hankintakustannukset :hankintataulukko-laskutukseen-perustuen]
                             [:hankintakustannukset :hankintataulukko])
          [janan-index solun-index] (tyokalut/osan-polku-taulukossa (get-in app polku-taulukkoon) osa)
          vanhemman-janan-id (:vanhempi (meta (get-in app (conj polku-taulukkoon janan-index))))
          vanhempi-jana (first (tyokalut/jana (get-in app polku-taulukkoon) vanhemman-janan-id))
          vanhemman-janan-index (tyokalut/janan-index (get-in app polku-taulukkoon) vanhempi-jana)
          yhteensa-index (first (keep-indexed (fn [index osa]
                                                (when (= (-> osa meta :sarake) :yhteensa)
                                                  index))
                                              (p/janan-osat vanhempi-jana)))
          polku-muutettuun-arvoon (if laskutuksen-perusteella-taulukko?
                                    (conj polku-taulukkoon janan-index :solut solun-index :parametrit :value)
                                    (conj polku-taulukkoon janan-index :solut solun-index :parametrit :value))
          polku-yhteensa-arvoon (if laskutuksen-perusteella-taulukko?
                                  (conj polku-taulukkoon vanhemman-janan-index :solut yhteensa-index :teksti)
                                  (conj polku-taulukkoon vanhemman-janan-index :solut yhteensa-index :teksti))]
      (-> app
          (update-in polku-yhteensa-arvoon
                     (fn [vanha-yhteensa]
                       (let [vanha-arvo (get-in app polku-muutettuun-arvoon)
                             lisays (- (js/parseInt arvo) (js/parseInt vanha-arvo))]
                         (str (+ (js/parseInt vanha-yhteensa) (js/parseInt lisays))))))
          (update-in polku-muutettuun-arvoon
                     (fn [_]
                       arvo))))))