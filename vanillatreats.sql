-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Oct 29, 2026 at 04:16 AM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.1.25

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `vanillatreats`
--

-- --------------------------------------------------------

--
-- Table structure for table `orders`
--

CREATE TABLE `orders` (
  `order_no` varchar(30) NOT NULL,
  `name` varchar(100) DEFAULT NULL,
  `email` varchar(150) DEFAULT NULL,
  `cake` varchar(100) DEFAULT NULL,
  `flavor` varchar(100) DEFAULT NULL,
  `size` varchar(50) DEFAULT NULL,
  `qty` int(11) DEFAULT NULL,
  `msg` text DEFAULT NULL,
  `notes` text DEFAULT NULL,
  `img` text DEFAULT NULL,
  `method` varchar(50) DEFAULT NULL,
  `addr` text DEFAULT NULL,
  `status` varchar(30) DEFAULT NULL,
  `pm` varchar(50) DEFAULT NULL,
  `hour_slot` int(11) DEFAULT NULL,
  `order_date` varchar(20) DEFAULT NULL,
  `sub` double DEFAULT NULL,
  `fee` double DEFAULT NULL,
  `disc` double DEFAULT NULL,
  `total` double DEFAULT NULL,
  `refunded` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `orders`
--

INSERT INTO `orders` (`order_no`, `name`, `email`, `cake`, `flavor`, `size`, `qty`, `msg`, `notes`, `img`, `method`, `addr`, `status`, `pm`, `hour_slot`, `order_date`, `sub`, `fee`, `disc`, `total`, `refunded`) VALUES
('VT-2026-00122', 'Jon Reyes', 'jon@example.com', 'Ube Dream', 'Ube', '10\"', 1, '', '', '', 'Delivery', 'Sample address', 'Pending', 'GCash', 14, '2026-10-16', 1805, 150, 0, 1955, 0),
('VT-2026-00123', 'Maria Santos', 'maria@example.com', 'Strawberry Bliss', 'Strawberry', '8\"', 1, '', '', '', 'Pickup', '', 'Preparing', 'GCash', 10, '2026-10-18', 1330, 0, 0, 1330, 0),
('VT-2026-00124', 'Maria Santos', 'maria@example.com', 'Classic Vanilla', 'Vanilla', '6\"', 2, '', '', '', 'Pickup', '', 'Ready', 'GCash', 13, '2026-10-03', 1700, 0, 0, 1700, 1),
('VT-2026-00126', 'kyla', 'kyla@gmail.com', 'Chocolate Dream', 'Chocolate', '6\"', 1, 'happy birthday', '', '', 'Pickup', '', 'Pending', 'Cash', 10, '2026-10-22', 950, 0, 0, 950, 0),
('VT-2026-00127', 'kyla', 'kyla@gmail.com', 'Chocolate Dream', 'Chocolate', '6\"', 1, '', '', '', 'Pickup', '', 'Payment Confirmed', 'GCash', 10, '2026-10-15', 950, 0, 0, 950, 0),
('VT-2026-00128', 'Maria Santos', 'maria@example.com', 'Chocolate Dream', 'Chocolate', '6\"', 1, '', '', 'C:\\users\\acer\\Pictures\\Saved Pictures\\Facebook_files\\453178253_471506465671661_2781666950760530985_n(1).png', 'Pickup', '', 'Payment Confirmed', 'GCash', 10, '2026-10-16', 899, 0, 0, 899, 0);

-- --------------------------------------------------------

--
-- Table structure for table `payments`
--

CREATE TABLE `payments` (
  `id` int(11) NOT NULL,
  `order_no` varchar(30) DEFAULT NULL,
  `paid_at` varchar(50) DEFAULT NULL,
  `method` varchar(50) DEFAULT NULL,
  `kind` varchar(30) DEFAULT NULL,
  `amount` double DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `payments`
--

INSERT INTO `payments` (`id`, `order_no`, `paid_at`, `method`, `kind`, `amount`) VALUES
(56, 'VT-2026-00123', '2026-10-12T17:54:59.586', 'GCash', 'payment', 665),
(57, 'VT-2026-00124', '2026-10-12T17:54:59.586', 'GCash', 'payment', 1700),
(58, 'VT-2026-00124', '2026-10-15T17:25:12.947', 'GCash', 'refund', 1700),
(59, 'VT-2026-00127', '2026-10-14T14:52:19.604', 'GCash', 'payment', 475),
(60, 'VT-2026-00128', '2026-10-15T17:16:09.942', 'GCash', 'payment', 450);

-- --------------------------------------------------------

--
-- Table structure for table `products`
--

CREATE TABLE `products` (
  `id` int(11) NOT NULL,
  `name` varchar(100) DEFAULT NULL,
  `flavor` varchar(100) DEFAULT NULL,
  `price` double DEFAULT NULL,
  `on_sale` int(11) DEFAULT NULL,
  `colors` varchar(100) DEFAULT NULL,
  `cats` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `products`
--

INSERT INTO `products` (`id`, `name`, `flavor`, `price`, `on_sale`, `colors`, `cats`) VALUES
(1, 'Red Velvet', 'Red velvet', 999, 1, '16774388,12727597,16777215,10691114', 'Best Sellers|Red Velvet|Birthday Cakes'),
(2, 'Ube Dream', 'Ube', 849, 1, '9331652,5978772,14272238,12031966', 'Best Sellers|Birthday Cakes'),
(3, 'Chocolate Dream', 'Chocolate', 899, 1, '5911591,10512188,9067066,5911591', 'Best Sellers|Chocolate Cakes'),
(4, 'Strawberry Bliss', 'Strawberry', 950, 1, '16163000,15024760,16500693,16506855', 'Best Sellers|Birthday Cakes'),
(5, 'Cookies and Cream', 'Cookies and cream', 950, 1, '15722207,3877414,3877414,15722207', 'Cheesecakes|Custom Cakes'),
(6, 'Mango Delight', 'Mango', 1000, 1, '16169275,15044134,16507808,16642248', 'Cheesecakes'),
(7, 'Black Forest', 'Chocolate', 1050, 1, '16777215,11609914,4860450,4860450', 'Chocolate Cakes|Birthday Cakes'),
(8, 'Classic Vanilla', 'Vanilla', 850, 1, '16180928,14862730,16180928,16445656', 'Birthday Cakes|Custom Cakes'),
(9, 'New cake', 'Vanilla', 900, 1, '16500693,15024760,16777215,16506855', '');

-- --------------------------------------------------------

--
-- Table structure for table `settings`
--

CREATE TABLE `settings` (
  `id` int(11) NOT NULL,
  `cap` int(11) DEFAULT NULL,
  `slot_cap` int(11) DEFAULT NULL,
  `policy` int(11) DEFAULT NULL,
  `lead` int(11) DEFAULT NULL,
  `fee` double DEFAULT NULL,
  `pay` varchar(30) DEFAULT NULL,
  `open_hours` varchar(100) DEFAULT NULL,
  `close_hours` varchar(100) DEFAULT NULL,
  `blocked` text DEFAULT NULL,
  `seq` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `settings`
--

INSERT INTO `settings` (`id`, `cap`, `slot_cap`, `policy`, `lead`, `fee`, `pay`, `open_hours`, `close_hours`, `blocked`, `seq`) VALUES
(1, 5, 2, 24, 1, 150, 'deposit', '-1,9,9,9,9,9,10', '0,17,17,17,17,17,15', '', 128);

-- --------------------------------------------------------

--
-- Table structure for table `users`
--

CREATE TABLE `users` (
  `email` varchar(150) NOT NULL,
  `name` varchar(100) DEFAULT NULL,
  `pass_hash` varchar(100) DEFAULT NULL,
  `answer_hash` varchar(100) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `users`
--

INSERT INTO `users` (`email`, `name`, `pass_hash`, `answer_hash`) VALUES
('@gmail', 'kirlyn mae', '325de28158d57d3d93357a417b33a59e3bd75223b1dcb05d5194efdc84ff4040', '8401154c9ebd8e2d81b92eec766a4e34b6f1ea299f27922bc0872210dfb008f3'),
('kyla@gmail.com', 'kyla', '1812981dd90a9ef34d07e6bc8dd1c885f973c0315484cbd53bbea8c355cebf86', '542abbf179f2afbb2a9d813a6de66c9d5cf4fa5618c322c3ec9d9212ebc0ab40'),
('maria@example.com', 'Maria Santos', 'f109405980440947b4f47262667c1e4938fede26807dbe372cb3baf922e2fd1e', '3e78c3285ce1e100beab3a35e4f4cc6f19fa507a21177152be6ea83375fd3360'),
('maria@gmail', 'bolanio maria', '8dc8c52bcb420ee8542b3333c374a6194aabfa1d7af1cedf4a78e3c237e549ae', '18ddd1b8d68af8ec12980b69155d3cc28017c66b8ab1c6e13c5b58c981daa1f1'),
('maria@gmail.com', 'maria bobonio', 'd52f28b7b8e670d6c4e9f3f51a2d31c7275ca06ca6bc8f1f68bcb62b80983566', 'd6a903d9df5ff44746c78e8eb781c18565bd85f83b92409b25703cdb2c7901b8');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `orders`
--
ALTER TABLE `orders`
  ADD PRIMARY KEY (`order_no`);

--
-- Indexes for table `payments`
--
ALTER TABLE `payments`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `products`
--
ALTER TABLE `products`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `settings`
--
ALTER TABLE `settings`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `users`
--
ALTER TABLE `users`
  ADD PRIMARY KEY (`email`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `payments`
--
ALTER TABLE `payments`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=61;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
