-- Sarake tielupaliitteen URL:ille. Itse liitettä ei lähetetä, ainoastaan URL.
ALTER TABLE tielupa
ADD COLUMN "liite-url" TEXT;