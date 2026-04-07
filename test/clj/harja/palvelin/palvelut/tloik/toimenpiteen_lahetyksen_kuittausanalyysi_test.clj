(ns harja.palvelin.palvelut.tloik.toimenpiteen-lahetyksen-kuittausanalyysi-test
  (:require [clojure.test :refer [deftest is]]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.palvelut.tloik.toimenpiteen-lahetyksen-kuittausanalyysi :as kuittausanalyysi-palvelu]))

(defn- duplikaattiyhteenveto
  [ilmoitusid kuittaustyyppi kanava duplikaatteja kertyneet-lahetysvirheet uniikit-kuittaajat ensimmainen viimeisin uniikit-ulkoiset-idt]
  {:ryhmaavain (str ilmoitusid "|" kuittaustyyppi "|" kanava)
   :ilmoitusid ilmoitusid
   :kuittaustyyppi kuittaustyyppi
   :kanava kanava
   :duplikaatteja duplikaatteja
   :kertyneet_lahetysvirheet kertyneet-lahetysvirheet
   :uniikit_kuittaajat uniikit-kuittaajat
   :ensimmainen_alkanut ensimmainen
   :viimeisin_alkanut viimeisin
   :uniikit_ulkoiset_idt uniikit-ulkoiset-idt})

(defn- esimerkkitapahtuma
  [ilmoitusid kuittaustyyppi kanava tapahtuma-id ulkoinen-id alkanut]
  {:ryhmaavain (str ilmoitusid "|" kuittaustyyppi "|" kanava)
   :tapahtumaid tapahtuma-id
   :ulkoinenid ulkoinen-id
   :alkanut alkanut})

(deftest muodosta-vastaus-katkaisee-haun-ryhmatasolla
  (let [yhteenvedot (vec (for [indeksi (range 0 1001)]
                           (duplikaattiyhteenveto indeksi
                                                  "aloitus"
                                                  "harja"
                                                  2
                                                  3
                                                  1
                                                  #inst "2026-03-20T08:00:00.000-00:00"
                                                  #inst "2026-03-20T08:10:00.000-00:00"
                                                  [(str "msg-" indeksi)])))
        esimerkit (vec (for [indeksi (range 0 1000)]
                         (esimerkkitapahtuma indeksi
                                             "aloitus"
                                             "harja"
                                             indeksi
                                             (str "msg-" indeksi)
                                             #inst "2026-03-20T08:10:00.000-00:00")))
        vastaus (#'kuittausanalyysi-palvelu/muodosta-vastaus
                  yhteenvedot
                  {:kasitellyt_rivit 2500
                   :ohitetut_rivit 12}
                  esimerkit)]
    (is (= 1000 (count (:ryhmat vastaus))))
    (is (= 0 (-> vastaus :ryhmat first :ilmoitusid)))
    (is (= 999 (-> vastaus :ryhmat last :ilmoitusid)))
    (is (= "harja" (-> vastaus :ryhmat first :kanava)))
    (is (= 3 (-> vastaus :ryhmat first :kertyneet-lahetysvirheet)))
    (is (= 1 (-> vastaus :ryhmat first :uniikit-kuittaajat)))
    (is (= 1 (count (-> vastaus :ryhmat first :esimerkkitapahtumat))))
    (is (= 2500 (:kasitellyt-rivit vastaus)))
    (is (= 12 (:ohitetut-rivit vastaus)))
    (is (true? (:katkaistu vastaus)))))

(deftest muodosta-vastaus-sailyttaa-duplikaatit-ja-lahetysvirheet-kanavittain
  (let [pgarray-sentinel (Object.)
        yhteenvedot [(duplikaattiyhteenveto 123 "aloitus" "harja" 4 2 1
                                            #inst "2026-03-10T08:00:00.000-00:00"
                                            #inst "2026-03-10T08:15:00.000-00:00"
                                            pgarray-sentinel)
                    (duplikaattiyhteenveto 456 "lopetus" "sms" 0 3 2
                                            #inst "2026-03-11T09:00:00.000-00:00"
                                            #inst "2026-03-11T09:10:00.000-00:00"
                                            ["msg-3" "msg-4"])]
        esimerkit [(esimerkkitapahtuma 123 "aloitus" "harja" 100 "msg-2" #inst "2026-03-10T08:15:00.000-00:00")
                   (esimerkkitapahtuma 123 "aloitus" "harja" 99 "msg-1" #inst "2026-03-10T08:05:00.000-00:00")
                   (esimerkkitapahtuma 456 "lopetus" "sms" 200 "msg-4" #inst "2026-03-11T09:10:00.000-00:00")]
        odotetut-ryhmat [{:ilmoitusid 123
                          :kuittaustyyppi "aloitus"
                          :kanava "harja"
                          :duplikaatteja 4
                          :kertyneet-lahetysvirheet 2
                          :uniikit-kuittaajat 1
                          :ensimmainen-alkanut #inst "2026-03-10T08:00:00.000-00:00"
                          :viimeisin-alkanut #inst "2026-03-10T08:15:00.000-00:00"
                          :uniikit-ulkoiset-idt ["msg-1" "msg-2"]
                          :esimerkkitapahtumat [{:tapahtuma-id 100
                                                 :alkanut #inst "2026-03-10T08:15:00.000-00:00"
                                                 :ulkoinen-id "msg-2"}
                                                {:tapahtuma-id 99
                                                 :alkanut #inst "2026-03-10T08:05:00.000-00:00"
                                                 :ulkoinen-id "msg-1"}]}
                         {:ilmoitusid 456
                          :kuittaustyyppi "lopetus"
                          :kanava "sms"
                          :duplikaatteja 0
                          :kertyneet-lahetysvirheet 3
                          :uniikit-kuittaajat 2
                          :ensimmainen-alkanut #inst "2026-03-11T09:00:00.000-00:00"
                          :viimeisin-alkanut #inst "2026-03-11T09:10:00.000-00:00"
                          :uniikit-ulkoiset-idt ["msg-3" "msg-4"]
                          :esimerkkitapahtumat [{:tapahtuma-id 200
                                                 :alkanut #inst "2026-03-11T09:10:00.000-00:00"
                                                 :ulkoinen-id "msg-4"}]}]]
    (with-redefs [konversio/pgarray->vector
                  (fn [arvo]
                    (when (identical? arvo pgarray-sentinel)
                      ["msg-1" "msg-2"]))]
      (let [vastaus (#'kuittausanalyysi-palvelu/muodosta-vastaus
                      yhteenvedot
                      {:kasitellyt_rivit 15
                       :ohitetut_rivit 1}
                      esimerkit)]
        (is (= odotetut-ryhmat (:ryhmat vastaus)))
        (is (= 15 (:kasitellyt-rivit vastaus)))
        (is (= 1 (:ohitetut-rivit vastaus)))
        (is (false? (:katkaistu vastaus)))))))
