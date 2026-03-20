(ns harja.palvelin.palvelut.integraatioloki-test
  (:require [clojure.test :refer [deftest is]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.integraatioloki :as integraatioloki-q]
            [harja.palvelin.palvelut.integraatioloki :as integraatioloki-palvelu]))

(defn- duplikaattiyhteenveto
  [ilmoitusid kuittaustyyppi maara ensimmainen viimeisin uniikit-ulkoiset-idt]
  {:ryhmaavain (str ilmoitusid "|" kuittaustyyppi)
   :ilmoitusid ilmoitusid
   :kuittaustyyppi kuittaustyyppi
   :maara maara
   :ensimmainen_alkanut ensimmainen
   :viimeisin_alkanut viimeisin
   :uniikit_ulkoiset_idt uniikit-ulkoiset-idt})

(defn- esimerkkitapahtuma
  [ilmoitusid kuittaustyyppi tapahtuma-id ulkoinen-id alkanut]
  {:ryhmaavain (str ilmoitusid "|" kuittaustyyppi)
   :tapahtumaid tapahtuma-id
   :ulkoinenid ulkoinen-id
   :alkanut alkanut})

(deftest hae-epaillyt-duplikaattikuittaukset-katkaisee-haun-ryhmatasolla
  (let [yhteenvetoparametrit (atom nil)
        esimerkkiparametrit (atom nil)
        yhteenvedot (vec (for [indeksi (range 0 1001)]
                           (duplikaattiyhteenveto indeksi
                                                 "aloitus"
                                                 2
                                                 #inst "2026-03-20T08:00:00.000-00:00"
                                                 #inst "2026-03-20T08:10:00.000-00:00"
                                                 [(str "msg-" indeksi)])))
        esimerkit (vec (for [indeksi (range 0 1000)]
                         (esimerkkitapahtuma indeksi
                                             "aloitus"
                                             indeksi
                                             (str "msg-" indeksi)
                                             #inst "2026-03-20T08:10:00.000-00:00")))]
    (with-redefs [oikeudet/vaadi-lukuoikeus (fn [& _])
                  integraatioloki-q/hae-tloik-toimenpiteen-lahetyksen-duplikaattikuittausyhteenvedot
                  (fn [_db parametrit]
                    (reset! yhteenvetoparametrit parametrit)
                    yhteenvedot)
                  integraatioloki-q/hae-tloik-toimenpiteen-lahetyksen-duplikaattikuittausten-tilastot
                  (fn [_db _]
                    {:kasitellyt_rivit 2500
                     :ohitetut_rivit 12})
                  integraatioloki-q/hae-tloik-toimenpiteen-lahetyksen-duplikaattikuittausten-esimerkkitapahtumat
                  (fn [_db parametrit]
                    (reset! esimerkkiparametrit parametrit)
                    esimerkit)]
      (let [vastaus (integraatioloki-palvelu/hae-epaillyt-duplikaattikuittaukset
                      :db
                      {:id 1}
                      #inst "2026-03-20T00:00:00.000-00:00"
                      #inst "2026-03-20T23:59:59.000-00:00")]
        (is (= 1001 (:limit @yhteenvetoparametrit)) "Kysely hakee yhden ylimääräisen ryhmän katkaisun tunnistamiseksi")
        (is (= 1000 (count (:ryhmat vastaus))))
        (is (= 1000 (count (:ryhmaavaimet @esimerkkiparametrit))))
        (is (= "0|aloitus" (first (:ryhmaavaimet @esimerkkiparametrit))))
        (is (= "999|aloitus" (last (:ryhmaavaimet @esimerkkiparametrit))))
        (is (= 2500 (:kasitellyt-rivit vastaus)))
        (is (= 12 (:ohitetut-rivit vastaus)))
        (is (true? (:katkaistu vastaus)))))))

(deftest hae-epaillyt-duplikaattikuittaukset-sailyttaa-vanhemmatkin-ryhmat-kun-rajaus-tehdaan-ryhmista
  (let [yhteenvedot [(duplikaattiyhteenveto 123 "aloitus" 4
                                            #inst "2026-03-10T08:00:00.000-00:00"
                                            #inst "2026-03-10T08:15:00.000-00:00"
                                            ["msg-1" "msg-2"])
                    (duplikaattiyhteenveto 456 "lopetus" 2
                                            #inst "2026-03-11T09:00:00.000-00:00"
                                            #inst "2026-03-11T09:10:00.000-00:00"
                                            ["msg-3" "msg-4"])]
        esimerkit [(esimerkkitapahtuma 123 "aloitus" 100 "msg-2" #inst "2026-03-10T08:15:00.000-00:00")
                   (esimerkkitapahtuma 123 "aloitus" 99 "msg-1" #inst "2026-03-10T08:05:00.000-00:00")
                   (esimerkkitapahtuma 456 "lopetus" 200 "msg-4" #inst "2026-03-11T09:10:00.000-00:00")]
        odotetut-ryhmat [{:ilmoitusid 123
                          :kuittaustyyppi "aloitus"
                          :maara 4
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
                          :maara 2
                          :ensimmainen-alkanut #inst "2026-03-11T09:00:00.000-00:00"
                          :viimeisin-alkanut #inst "2026-03-11T09:10:00.000-00:00"
                          :uniikit-ulkoiset-idt ["msg-3" "msg-4"]
                          :esimerkkitapahtumat [{:tapahtuma-id 200
                                                 :alkanut #inst "2026-03-11T09:10:00.000-00:00"
                                                 :ulkoinen-id "msg-4"}]}]]
    (with-redefs [oikeudet/vaadi-lukuoikeus (fn [& _])
                  integraatioloki-q/hae-tloik-toimenpiteen-lahetyksen-duplikaattikuittausyhteenvedot
                  (fn [_db _]
                    yhteenvedot)
                  integraatioloki-q/hae-tloik-toimenpiteen-lahetyksen-duplikaattikuittausten-tilastot
                  (fn [_db _]
                    {:kasitellyt_rivit 15
                     :ohitetut_rivit 1})
                  integraatioloki-q/hae-tloik-toimenpiteen-lahetyksen-duplikaattikuittausten-esimerkkitapahtumat
                  (fn [_db _]
                    esimerkit)]
      (let [vastaus (integraatioloki-palvelu/hae-epaillyt-duplikaattikuittaukset
                      :db
                      {:id 1}
                      #inst "2026-03-10T00:00:00.000-00:00"
                      #inst "2026-03-11T23:59:59.000-00:00")]
        (is (= odotetut-ryhmat (:ryhmat vastaus)))
        (is (= 15 (:kasitellyt-rivit vastaus)))
        (is (= 1 (:ohitetut-rivit vastaus)))
        (is (false? (:katkaistu vastaus)))))))
